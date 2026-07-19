package com.wevo.backend.opinion.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateCloseResponse;
import com.wevo.backend.opinion.dto.response.OpinionGateReopenResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.dto.response.SubmittedOpinionListResponse;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.SectionStatusService;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 의견(my-opinion) 관련 로직. (제품 정책서 §4·§5)
 *
 * <ul>
 *   <li>한 멤버는 한 섹션에 <b>하나의 의견</b>만 가진다 — 임시저장(upsert)이 생성을 겸한다.</li>
 *   <li>의견 저장은 섹션이 의견 수집 단계({@code COLLECTING})일 때만 가능하다.
 *       수집 마감(gate CLOSED) 시 섹션은 {@code SYNTHESIZING} 으로 전이되므로,
 *       별도 게이트 컬럼 없이 섹션 상태로 판정한다.</li>
 *   <li><b>재제출 모델(§4.1)</b> — 임시저장은 작업본만 갱신하고 제출본은 유지된다.
 *       재제출하면 제출본이 최신 작업본으로 갱신된다.</li>
 * </ul>
 *
 * <p>의견 API는 OWNER와 MEMBER 모두 사용할 수 있으므로 프로젝트 참여 여부만 확인하며,
 * 공통 인가 규칙(비멤버 404 존재 숨김 포함)은 {@link SectionAccessGuard}에 위임한다.
 */
@Service
@Transactional(readOnly = true)
public class OpinionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SectionAccessGuard sectionAccessGuard;
    private final OpinionRepository opinionRepository;
    private final SectionStatusService sectionStatusService;
    private final UserRepository userRepository;

    public OpinionService(SectionAccessGuard sectionAccessGuard,
                          OpinionRepository opinionRepository,
                          SectionStatusService sectionStatusService,
                          UserRepository userRepository) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.opinionRepository = opinionRepository;
        this.sectionStatusService = sectionStatusService;
        this.userRepository = userRepository;
    }

    /**
     * 내 의견을 임시저장한다. 의견이 없으면 DRAFT 로 새로 만들고, 있으면 본문을 덮어쓴다.
     *
     * <p>섹션 행을 배타 잠금으로 조회해 조회→INSERT 사이의 경합을 직렬화한다.
     * 같은 사용자의 자동저장 요청이 겹쳐도 유니크 제약 충돌(409) 없이 뒤 요청이 덮어쓴다.
     *
     * <p>잠금 범위를 섹션 단위로 둔 것은 의도된 선택이다 — 프로젝트 인원이 1~4명(정책)이라
     * 직렬화 비용이 무시 가능하고, 이후 수집 마감(gate close)과 저장이 겹치는 경합도 같은 잠금으로
     * 막는다. 더 좁은 잠금(의견 행)은 최초 저장 시 잠글 행이 없어 INSERT 경합을 막지 못한다.
     */
    @Transactional
    public OpinionDraftResponse saveDraft(Long projectSectionId, Long userId, OpinionDraftRequest request) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.OPINION_COLLECTION_CLOSED);
        }

        Opinion opinion = opinionRepository.findByProjectSection_IdAndAuthor_Id(projectSectionId, userId)
                .map(existing -> {
                    existing.updateContent(request.content());
                    // 이미 영속 상태이므로 save 없이 flush 만으로 auditing(updatedAt)을 응답에 반영한다.
                    opinionRepository.flush();
                    return existing;
                })
                .orElseGet(() -> opinionRepository.save(Opinion.builder()
                        .projectSection(section)
                        .author(userRepository.getReferenceById(userId))
                        .content(request.content())
                        .status(OpinionStatus.DRAFT)
                        .build()));

        return OpinionDraftResponse.from(opinion);
    }

    /**
     * 내 의견(작업본 기준)을 조회한다. 아직 작성하지 않았으면 예외가 아니라 {@code exists=false} 응답을 반환한다.
     */
    public MyOpinionResponse getMyOpinion(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(projectSectionId, userId);
        return opinionRepository.findByProjectSection_IdAndAuthor_Id(projectSectionId, userId)
                .map(MyOpinionResponse::from)
                .orElseGet(MyOpinionResponse::empty);
    }

    /**
     * 섹션에 제출된 팀원 의견 목록을 조회한다. (정책서 §5 공개 게이트)
     *
     * <p>요청자가 이 섹션에 제출한 적이 없으면 목록을 숨기고 제출 건수만 반환한다(베끼기 방지).
     * 게이트 판정은 "내 SUBMITTED 의견 존재"로 파생한다 — 의견 삭제가 정책상 미지원이라
     * 한 번 제출한 사실은 사라지지 않는다.
     *
     * <p>섹션 상태와 무관하게 조회할 수 있다 — 수집 마감 후에도 정리(SYNTHESIZING) 단계에서
     * 팀이 근거 의견을 봐야 한다. DRAFT 의견은 쿼리 단계에서 제외되어 어떤 경우에도 노출되지 않는다.
     */
    public SubmittedOpinionListResponse getSubmittedOpinions(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireParticipantSection(projectSectionId, userId);
        List<Opinion> submitted = opinionRepository
                .findAllWithAuthorByProjectSectionIdAndStatus(projectSectionId, OpinionStatus.SUBMITTED);

        boolean everSubmitted = submitted.stream()
                .anyMatch(opinion -> opinion.getAuthor().getId().equals(userId));
        if (!everSubmitted) {
            return SubmittedOpinionListResponse.hidden(submitted.size());
        }
        return SubmittedOpinionListResponse.visible(submitted);
    }

    /**
     * 내 작업본을 팀에 제출한다. 재편집 후 재제출하면 제출본이 최신 작업본으로 갱신된다. (§4.1)
     *
     * <p>섹션 단위 배타 잠금으로 임시저장·제출·수집 마감 경합을 직렬화한다.
     * <b>작업본 변경 없이 다시 호출하면</b> 수집 마감 이후라도 최초 제출 시각을 유지한 채
     * 멱등 성공으로 응답한다. 작업본이 바뀐 재제출은 게이트가 열려 있어야 한다(§4.4).
     */
    @Transactional
    public OpinionSubmitResponse submitMyOpinion(Long projectSectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSectionForUpdate(projectSectionId, userId);
        Optional<Opinion> opinionOptional = opinionRepository
                .findByProjectSection_IdAndAuthor_Id(projectSectionId, userId);

        if (opinionOptional.isPresent()
                && opinionOptional.get().getStatus() == OpinionStatus.SUBMITTED
                && !opinionOptional.get().hasUnsubmittedChanges()) {
            return OpinionSubmitResponse.from(opinionOptional.get());
        }

        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.OPINION_COLLECTION_CLOSED);
        }

        Opinion opinion = opinionOptional.orElseThrow(() ->
                new BusinessException(
                        ErrorCode.OPINION_NOT_FOUND,
                        List.of(new FieldError(
                                "projectSectionId",
                                "no draft opinion to submit"
                        ))
                ));
        validateContentForSubmit(opinion.getContent());
        opinion.submit(LocalDateTime.now(KST));
        return OpinionSubmitResponse.from(opinion);
    }

    /**
     * 의견 수집을 마감한다. 마감은 OWNER만 실행할 수 있고 제출 의견이 하나 이상 있어야 한다.
     * 섹션 잠금을 먼저 획득해 늦게 도착한 임시저장·제출 요청과 상태 전이가 섞이지 않게 한다.
     */
    @Transactional
    public OpinionGateCloseResponse closeOpinionGate(Long projectSectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSectionForUpdate(projectSectionId, userId);
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        if (!opinionRepository.existsByProjectSection_IdAndStatus(projectSectionId, OpinionStatus.SUBMITTED)) {
            throw new BusinessException(ErrorCode.NO_SUBMITTED_OPINION);
        }

        sectionStatusService.markSynthesizing(projectSectionId, userId);
        return OpinionGateCloseResponse.from(section, LocalDateTime.now(KST));
    }

    /**
     * 미확정 섹션의 의견 수집을 다시 연다. 확정 섹션은 재오픈할 수 없고, 기존 초안이 있으면
     * 재정리 필요 플래그가 설정된다.
     */
    @Transactional
    public OpinionGateReopenResponse reopenOpinionGate(Long projectSectionId, Long userId) {
        sectionAccessGuard.requireOwnedSectionForUpdate(projectSectionId, userId);

        ProjectSection reopened = sectionStatusService.markCollecting(projectSectionId, userId);
        return OpinionGateReopenResponse.from(reopened, LocalDateTime.now(KST));
    }

    private void validateContentForSubmit(String content) {
        if (content == null || content.isBlank()
                || content.length() < Opinion.MIN_CONTENT_LENGTH
                || content.length() > Opinion.MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    List.of(new FieldError(
                            "content",
                            "content must be between " + Opinion.MIN_CONTENT_LENGTH
                                    + " and " + Opinion.MAX_CONTENT_LENGTH + " characters"
                    ))
            );
        }
    }
}
