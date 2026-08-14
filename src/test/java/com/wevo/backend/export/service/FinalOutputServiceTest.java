package com.wevo.backend.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.ProjectOutputHeader;
import com.wevo.backend.project.service.ProjectOutputQueryService;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 확정 진행도와 확정본이 <b>서로 다른 스냅샷</b>을 봤을 때의 판정을 고정한다.
 *
 * <p>두 읽기는 같은 트랜잭션이어도 별개의 SQL 이고, READ COMMITTED 에서는 그 사이에 커밋된 확정
 * 해제가 보인다. 이때 "동시 해제"(재조회로 해소)와 "정합성 붕괴"(초안 누락)를 같은 실패로 묶으면
 * 정상 동시 편집이 500 으로 나간다. 그래서 두 경우의 응답이 갈린다는 것을 여기서 못 박는다.
 *
 * <p>동시 커밋은 DB 로 재현하기 어려우므로, 조회 창구가 <b>호출 순서에 따라 다른 값</b>을 돌려주게
 * 해서 같은 상황을 만든다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinalOutputServiceTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long USER_ID = 3L;

    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private ProjectOutputQueryService projectOutputQueryService;
    @Mock private SectionConfirmationQueryService sectionConfirmationQueryService;
    @Mock private FinalOutputFormatter finalOutputFormatter;
    @Mock private FinalOutputFileNamer finalOutputFileNamer;
    @Mock private VerifiedProjectAccess access;

    private FinalOutputService finalOutputService;

    @BeforeEach
    void setUp() {
        finalOutputService = new FinalOutputService(
                projectAccessGuard, projectOutputQueryService, sectionConfirmationQueryService,
                finalOutputFormatter, finalOutputFileNamer);
        given(projectAccessGuard.requireParticipantAccess(PROJECT_ID, USER_ID)).willReturn(access);
        given(access.projectId()).willReturn(PROJECT_ID);
        given(projectOutputQueryService.getOutputHeader(access)).willReturn(
                new ProjectOutputHeader(PROJECT_ID, "위보 기획", OutputType.PROPOSAL));
    }

    @Test
    @DisplayName("전 섹션 확정이고 확정본 수도 맞으면 완성본을 조립한다")
    void assemblesWhenProgressAndContentsAgree() {
        givenProgress(summary(2, 2));
        givenContents(content(1, "문제 정의"), content(2, "해결 방안"));

        FinalOutputResponse output = finalOutputService.getFinalOutput(PROJECT_ID, USER_ID);

        assertThat(output.ready()).isTrue();
        assertThat(output.sections()).hasSize(2);
    }

    @Test
    @DisplayName("조립 중 다른 사용자가 확정을 해제하면 500 이 아니라 ready=false 로 되돌린다")
    void concurrentUnconfirmFallsBackToNotReady() {
        // 진행도를 읽은 뒤 섹션 하나가 해제됐다 — 조립 쿼리는 CONFIRMED 인 1건만 본다.
        givenProgress(summary(2, 2), summary(2, 1));
        givenContents(content(1, "문제 정의"));

        FinalOutputResponse output = finalOutputService.getFinalOutput(PROJECT_ID, USER_ID);

        assertThat(output.ready()).isFalse();
        assertThat(output.sections()).isNull();
        // 진행도는 되돌아간 최신 수치여야 FE 가 "1/2 확정"으로 정확히 안내한다.
        assertThat(output.confirmedCount()).isEqualTo(1);
        assertThat(output.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("복사·다운로드는 동시 해제를 409(C003)로 거절해 재조회를 유도한다")
    void concurrentUnconfirmRejectsDerivedApisWithConflict() {
        givenProgress(summary(2, 2), summary(2, 1));
        givenContents(content(1, "문제 정의"));

        assertThatThrownBy(() -> finalOutputService.getFormattedOutput(
                PROJECT_ID, USER_ID, FinalOutputFormat.PLAIN_TEXT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("여전히 전 섹션 확정인데 확정본이 모자라면 정합성 붕괴로 500(E001)")
    void missingConfirmedDraftStaysServerError() {
        // 재확인에서도 2/2 확정 — 해제가 아니라 confirmedVersion 초안이 실제로 없는 상태다.
        givenProgress(summary(2, 2), summary(2, 2));
        givenContents(content(1, "문제 정의"));

        assertThatThrownBy(() -> finalOutputService.getFinalOutput(PROJECT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FINAL_OUTPUT_ASSEMBLY_FAILED);
    }

    @Test
    @DisplayName("섹션이 동시에 삭제돼 최신 진행도와 확정본 수가 맞으면 그대로 조립한다")
    void concurrentSectionRemovalStillAssembles() {
        // 진행도는 2/2 였지만 조립 시점엔 섹션이 1개 — 남은 1건이 전부 확정본이라 온전하다.
        givenProgress(summary(2, 2), summary(1, 1));
        givenContents(content(1, "문제 정의"));

        FinalOutputResponse output = finalOutputService.getFinalOutput(PROJECT_ID, USER_ID);

        assertThat(output.ready()).isTrue();
        assertThat(output.sections()).hasSize(1);
    }

    /** 첫 호출은 조회 진입 시점, 두 번째 호출은 어긋남을 가르는 재확인이다. */
    private void givenProgress(SectionConfirmationSummary first, SectionConfirmationSummary... rest) {
        given(sectionConfirmationQueryService.getConfirmationSummary(access))
                .willReturn(first, rest);
    }

    private void givenContents(ConfirmedSectionContent... contents) {
        given(sectionConfirmationQueryService.findConfirmedContents(access))
                .willReturn(List.of(contents));
        given(finalOutputFormatter.format(any(), any(), any())).willReturn("조립된 본문");
    }

    private SectionConfirmationSummary summary(int totalCount, int confirmedCount) {
        return new SectionConfirmationSummary(totalCount, confirmedCount);
    }

    private ConfirmedSectionContent content(int order, String title) {
        return new ConfirmedSectionContent(
                (long) order, null, order, 1, title, null, null, title + " 확정본");
    }
}
