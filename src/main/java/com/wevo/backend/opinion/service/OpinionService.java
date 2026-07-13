package com.wevo.backend.opinion.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 의견(my-opinion) 관련 로직. (제품 정책서 §5)
 *
 * <ul>
 *   <li>한 멤버는 한 섹션에 <b>하나의 의견</b>만 가진다 — 임시저장(upsert)이 생성을 겸한다.</li>
 *   <li>의견 저장은 섹션이 의견 수집 단계({@code COLLECTING})일 때만 가능하다.
 *       수집 마감(gate CLOSED) 시 섹션은 {@code SYNTHESIZING} 으로 전이되므로,
 *       별도 게이트 컬럼 없이 섹션 상태로 판정한다.</li>
 *   <li>SUBMITTED 의견을 다시 임시저장해도 상태는 SUBMITTED 로 유지된다(재제출 절차 없음).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class OpinionService {

    private final ProjectSectionRepository projectSectionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OpinionRepository opinionRepository;
    private final UserRepository userRepository;

    public OpinionService(ProjectSectionRepository projectSectionRepository,
                          ProjectMemberRepository projectMemberRepository,
                          OpinionRepository opinionRepository,
                          UserRepository userRepository) {
        this.projectSectionRepository = projectSectionRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.opinionRepository = opinionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 내 의견을 임시저장한다. 의견이 없으면 DRAFT 로 새로 만들고, 있으면 본문을 덮어쓴다.
     *
     * <p>섹션 행을 배타 잠금으로 조회해 조회→INSERT 사이의 경합을 직렬화한다.
     * 같은 사용자의 자동저장 요청이 겹쳐도 유니크 제약 충돌(409) 없이 뒤 요청이 덮어쓴다.
     */
    @Transactional
    public OpinionDraftResponse saveDraft(Long projectSectionId, Long userId, OpinionDraftRequest request) {
        ProjectSection section = requireMemberSectionForUpdate(projectSectionId, userId);
        if (section.getStatus() != ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.OPINION_COLLECTION_CLOSED);
        }

        Opinion opinion = opinionRepository.findByProjectSection_IdAndAuthor_Id(projectSectionId, userId)
                .map(existing -> {
                    existing.updateContent(request.content());
                    // 갱신된 updatedAt 을 응답에 반영하기 위해 즉시 flush 한다.
                    return opinionRepository.saveAndFlush(existing);
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
     * 섹션을 배타 잠금으로 조회하고 요청자가 그 프로젝트의 멤버인지 검증한 뒤 섹션을 반환한다.
     *
     * @throws BusinessException SECTION_NOT_FOUND(섹션 없음) / NOT_PROJECT_MEMBER(멤버 아님)
     */
    private ProjectSection requireMemberSectionForUpdate(Long projectSectionId, Long userId) {
        ProjectSection section = projectSectionRepository.findByIdForUpdate(projectSectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        if (!projectMemberRepository.existsByProjectIdAndUserId(section.getProject().getId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_PROJECT_MEMBER);
        }
        return section;
    }
}
