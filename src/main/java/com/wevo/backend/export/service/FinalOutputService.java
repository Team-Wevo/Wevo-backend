package com.wevo.backend.export.service;

import com.wevo.backend.export.dto.response.FinalOutputContentResponse;
import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.export.dto.response.FinalOutputResponse.SectionOutput;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.ProjectOutputHeader;
import com.wevo.backend.project.service.ProjectOutputQueryService;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최종 결과물(완성본) 조립. (API_SPEC §3.6.1, 정책서 §2.3)
 *
 * <p>확정된 섹션의 <b>확정본</b>({@code confirmedVersion} 기준 본문)을 섹션 순서대로 이어 붙여
 * 하나의 결과물로 반환한다.
 *
 * <p>미리보기는 FE가 {@link #getFinalOutput} 의 구조화 JSON으로 처리하고, 클립보드 복사
 * ({@link #getFormattedOutput})와 파일 다운로드({@link #getDownloadFile})용 <b>문자열 조립은
 * BE가 소유</b>한다. 세 경로의 포맷 정본이 FE·BE로 갈리지 않게 하기 위함이며,
 * 조립 규칙 자체는 {@link FinalOutputFormatter} 가 소유한다.
 *
 * <p>프로젝트·섹션 데이터는 리포지토리나 엔티티를 직접 보지 않고 각 도메인이 공개한 조회 창구
 * ({@link ProjectOutputQueryService}, {@link SectionConfirmationQueryService})로만 조회하며,
 * 받는 값도 엔티티가 아니라 값 객체({@link ProjectOutputHeader}, {@link ConfirmedSectionContent})다.
 * (CLAUDE.md §6)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class FinalOutputService {

    private final ProjectAccessGuard projectAccessGuard;
    private final ProjectOutputQueryService projectOutputQueryService;
    private final SectionConfirmationQueryService sectionConfirmationQueryService;
    private final FinalOutputFormatter finalOutputFormatter;
    private final FinalOutputFileNamer finalOutputFileNamer;

    public FinalOutputService(ProjectAccessGuard projectAccessGuard,
                              ProjectOutputQueryService projectOutputQueryService,
                              SectionConfirmationQueryService sectionConfirmationQueryService,
                              FinalOutputFormatter finalOutputFormatter,
                              FinalOutputFileNamer finalOutputFileNamer) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectOutputQueryService = projectOutputQueryService;
        this.sectionConfirmationQueryService = sectionConfirmationQueryService;
        this.finalOutputFormatter = finalOutputFormatter;
        this.finalOutputFileNamer = finalOutputFileNamer;
    }

    /**
     * 프로젝트의 최종 결과물을 조회한다. (프로젝트 멤버 전용)
     *
     * <p>모든 섹션이 {@code CONFIRMED} 일 때만 본문을 조립한다. 하나라도 미확정이면
     * {@code ready=false} 와 진행도만 반환한다. (정책서 §2.3 )
     *
     * <p><b>진행도와 확정본은 서로 다른 시점을 볼 수 있다</b> — 두 읽기는 같은 트랜잭션이지만 별개의
     * SQL 이고, PostgreSQL 기본 격리(READ COMMITTED)에서는 그 사이에 커밋된 다른 트랜잭션이 보인다.
     * 조회 도중 다른 사용자가 섹션 하나의 확정을 해제하면 확정본만 한 건 줄어든다. 이 어긋남을
     * 어떻게 가르는지는 {@link #assembleConfirmed} 가 소유한다.
     *
     * @throws BusinessException 프로젝트가 없거나 내가 멤버가 아니면 존재 숨김 규칙에 따라
     *                           {@link ErrorCode#PROJECT_NOT_FOUND}({@code P001})
     */
    public FinalOutputResponse getFinalOutput(Long projectId, Long userId) {
        VerifiedProjectAccess access = requireParticipantAccess(projectId, userId);
        ProjectOutputHeader project = projectOutputQueryService.getOutputHeader(access);

        SectionConfirmationSummary summary = sectionConfirmationQueryService.getConfirmationSummary(access);
        if (!summary.allConfirmed()) {
            return FinalOutputResponse.notReady(project, summary);
        }

        return assembleConfirmed(access, project, summary);
    }

    /**
     * 완성본을 클립보드 복사용 문자열로 조립해 반환한다. (프로젝트 멤버 전용)
     *
     * <p><b>조회 API 와 같은 확정 기준을 따른다</b> — 모든 섹션이 {@code CONFIRMED} 일 때만 조립하고,
     * 하나라도 미확정이면 {@code 409} 로 거절한다({@link #requireReadyOutput}). 일부만 담긴 문자열이
     * 완성본으로 외부에 공유되는 것을 막기 위함이며, 부분 반환을 하지 않는
     * {@link #getFinalOutput} 의 결정(정책서 §2.3)을 그대로 상속한다.
     *
     * @throws BusinessException 멤버가 아니면 {@link ErrorCode#PROJECT_NOT_FOUND}(존재 숨김),
     *                           미확정 섹션이 있으면 {@link ErrorCode#CONFLICT}
     */
    public FinalOutputContentResponse getFormattedOutput(
            Long projectId, Long userId, FinalOutputFormat format) {
        return new FinalOutputContentResponse(
                assemble(requireReadyOutput(projectId, userId), format));
    }

    /**
     * 완성본을 다운로드용 파일로 만들어 반환한다. (프로젝트 멤버 전용)
     *
     * <p><b>복사 API 와 완전히 같은 본문</b>이다 — 조립은 {@link FinalOutputFormatter} 가, 확정 기준과
     * 권한 검사는 {@link #requireReadyOutput} 이 소유하므로 두 경로가 다른 결과를 낼 수 없다.
     * 차이는 컨트롤러가 이 값을 JSON 으로 감싸는 대신 파일로 내려보낸다는 것뿐이다.
     *
     * <p>파일명은 <b>요청자가 지정한 이름</b>으로 만들고, 지정하지 않으면 프로젝트 제목을 쓴다.
     * 어느 쪽이든 파일명으로 쓸 수 없는 값이어도 다운로드를 실패시키지 않고
     * {@link FinalOutputFileNamer} 가 안전한 이름으로 정제한다 — 파일명 때문에 완성본을 못 받는
     * 상황을 만들지 않기 위함이다.
     *
     * @param requestedFileName 요청자가 지정한 파일명 (확장자 제외) — {@code null}·공백이면 프로젝트 제목
     * @throws BusinessException 멤버가 아니면 {@link ErrorCode#PROJECT_NOT_FOUND}(존재 숨김),
     *                           미확정 섹션이 있으면 {@link ErrorCode#CONFLICT}
     */
    public FinalOutputFile getDownloadFile(Long projectId, Long userId, FinalOutputFormat format,
                                           String requestedFileName) {
        FinalOutputResponse output = requireReadyOutput(projectId, userId);

        return new FinalOutputFile(
                finalOutputFileNamer.fileName(requestedFileName, output.title(), format),
                finalOutputFileNamer.asciiFileName(requestedFileName, output.title(), format),
                assemble(output, format));
    }

    /**
     * 완성본을 문자열로 조립한다. 복사·다운로드가 <b>같은 한 줄</b>을 쓰게 해서, 두 API 의 본문이
     * 갈릴 여지를 없앤다.
     */
    private String assemble(FinalOutputResponse output, FinalOutputFormat format) {
        return finalOutputFormatter.format(output.title(), output.sections(), format);
    }

    /**
     * 조립 가능한(전 섹션 확정된) 완성본을 가져온다. 복사·다운로드가 공유하는 진입 검사다.
     *
     * <p>조립 대상은 조회 API 가 내주는 것과 같은 완성본이므로 그 결과를 그대로 받는다.
     * 권한 검사·확정 판정·정합성 검사를 파생 API 마다 다시 쓰지 않아야, 규칙이 갈릴 수 없다.
     *
     * <p>미확정을 {@code CONFLICT}({@code C003})로 두는 이유: FE 는 {@code final-output} 조회의
     * {@code ready}·진행도로 이미 복사·다운로드 버튼 활성 여부를 판단할 수 있어, 이 응답의 원인을
     * 따로 분기할 필요가 없다. 전용 코드를 만들지 않는다. (CLAUDE.md §5.8)
     *
     * <p>조립 도중 다른 사용자가 확정을 해제한 경우도 여기로 들어온다 — {@code ready=false} 로
     * 되돌아오므로 같은 {@code 409} 다. 버튼을 누른 순간과 서버가 읽은 순간의 상태가 달랐다는
     * 뜻이고, FE 는 조회를 갱신해 진행도를 다시 보여주면 된다. (§3.6.1)
     */
    private FinalOutputResponse requireReadyOutput(Long projectId, Long userId) {
        FinalOutputResponse output = getFinalOutput(projectId, userId);
        if (!output.ready()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return output;
    }

    /**
     * 각 섹션의 확정본을 섹션 순서대로 조립한다. 확정본 수가 진행도와 어긋나면 그 원인을 가른다.
     *
     * <p>확정본이 모자란 원인은 두 가지이고, <b>대응이 정반대</b>다.
     *
     * <ul>
     *   <li><b>동시 확정 해제</b> — 진행도를 읽은 뒤 다른 사용자가 섹션의 확정을 해제하고 커밋했다.
     *       조립 쿼리의 {@code status = CONFIRMED} 조건이 그 섹션을 제외해 한 건이 준다.
     *       서버 데이터는 멀쩡하고 <b>클라이언트가 다시 조회하면 해소</b>되는 상태 변화이므로
     *       {@code ready=false} 로 되돌린다 (복사·다운로드는 {@link #requireReadyOutput} 에서
     *       {@code 409}). 이 경우까지 {@code 500} 으로 처리하면 정상 동시 편집이 서버 오류로 보이고
     *       운영 알람이 울린다.</li>
     *   <li><b>정합성 붕괴</b> — 섹션이 여전히 {@code CONFIRMED} 인데 {@code confirmedVersion} 에
     *       해당하는 초안이 없다. 확정 처리와 초안 이력이 어긋났다는 뜻이라 재조회로 해소되지 않고,
     *       클라이언트가 요청을 바꿔 풀 수 있는 상태 충돌도 아니다. {@code 500} 으로 실패시켜
     *       일부가 빠진 결과물을 완성본으로 내보내지 않는다.</li>
     * </ul>
     *
     * <p>둘을 가르는 방법은 <b>진행도를 다시 읽는 것</b>이다. 재확인에서도 전 섹션이 확정으로
     * 보이는데 확정본만 모자라면 초안이 실제로 없는 것이고, 미확정 섹션이 보이면 그 사이에
     * 해제가 일어난 것이다. 추가 조회는 어긋난 경우에만 나가므로 정상 경로의 비용은 그대로다.
     *
     * <p>재확인 진행도는 판정에만 쓰는 게 아니라 응답에도 그대로 싣는다 — 방금 읽은 최신 수치라
     * FE 가 진행도를 되돌려 보여주지 않는다.
     */
    private FinalOutputResponse assembleConfirmed(VerifiedProjectAccess access,
                                                  ProjectOutputHeader project,
                                                  SectionConfirmationSummary summary) {
        List<ConfirmedSectionContent> contents =
                sectionConfirmationQueryService.findConfirmedContents(access);
        if (contents.size() == summary.totalCount()) {
            return FinalOutputResponse.ready(project, sectionOutputs(contents));
        }

        SectionConfirmationSummary recheck =
                sectionConfirmationQueryService.getConfirmationSummary(access);
        if (!recheck.allConfirmed()) {
            log.info("완성본 조립 중 확정 상태가 바뀌어 재조회로 되돌림 — 확정 {}/{} 건, 확정본 {} 건. projectId={}",
                    recheck.confirmedCount(), recheck.totalCount(), contents.size(), access.projectId());
            return FinalOutputResponse.notReady(project, recheck);
        }
        if (contents.size() != recheck.totalCount()) {
            log.error("최종 결과물 조립 실패 — 확정 섹션 {} 개 중 확정본 {} 건만 조회됨. projectId={}",
                    recheck.totalCount(), contents.size(), access.projectId());
            throw new BusinessException(ErrorCode.FINAL_OUTPUT_ASSEMBLY_FAILED);
        }

        // 섹션 수 자체가 바뀌었지만(추가·삭제) 최신 진행도와는 맞는 경우 — 온전한 완성본이다.
        return FinalOutputResponse.ready(project, sectionOutputs(contents));
    }

    private List<SectionOutput> sectionOutputs(List<ConfirmedSectionContent> contents) {
        return contents.stream()
                .map(content -> new SectionOutput(content.order(), content.title(), content.content()))
                .toList();
    }

    /**
     * 요청자가 프로젝트 참여자인지 검증하고, 검증 증거를 반환한다.
     *
     * <p>멤버가 아니면 {@code 404 P001} 로 <b>존재 자체를 숨긴다</b>
     * (CLAUDE.md §5.6) — 순번 ID 를 훑어 남의 프로젝트 존재 여부를 알아내지 못하게 한다.
     * 숨김 규칙 자체는 {@link ProjectAccessGuard#hidingNonMember} 가 소유한다.
     */
    private VerifiedProjectAccess requireParticipantAccess(Long projectId, Long userId) {
        return ProjectAccessGuard.hidingNonMember(ErrorCode.PROJECT_NOT_FOUND,
                () -> projectAccessGuard.requireParticipantAccess(projectId, userId));
    }
}
