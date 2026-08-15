package com.wevo.backend.section.domain;

import com.wevo.backend.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectSectionTest {

    @Test
    @DisplayName("생성 직후 overlay 는 기본값이다 — driftStatus=NONE, aiCheckStatus 없음, synthesisStale=false, 미확정(0)")
    void newSection_hasDefaultOverlayValues() {
        ProjectSection section = section();

        assertThat(section.getDriftStatus()).isEqualTo(DriftStatus.NONE);
        assertThat(section.getAiCheckStatus()).isNull();
        assertThat(section.isSynthesisStale()).isFalse();
        assertThat(section.getOpinionGateGeneration()).isZero();
        assertThat(section.getConfirmedVersion()).isZero();
    }

    @Test
    @DisplayName("생성 직후에도 마지막 활동 시각이 비어 있지 않다 — 목록이 빈 값을 분기하지 않아도 된다")
    void newSection_hasLastActivityAt() {
        assertThat(section().getLastActivityAt()).isNotNull();
    }

    @Test
    @DisplayName("활동을 기록하면 마지막 활동 시각이 그 시점으로 옮겨간다")
    void recordActivity_movesLastActivityForward() {
        ProjectSection section = section();
        LocalDateTime later = section.getLastActivityAt().plusHours(1);

        section.recordActivity(later);

        assertThat(section.getLastActivityAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("더 이른 시각이나 null 은 무시한다 — 마지막 활동이 과거로 밀리지 않는다")
    void recordActivity_ignoresEarlierAndNull() {
        ProjectSection section = section();
        LocalDateTime initial = section.getLastActivityAt();

        section.recordActivity(initial.minusDays(1));
        section.recordActivity(null);

        assertThat(section.getLastActivityAt()).isEqualTo(initial);
    }

    @Test
    @DisplayName("상태 전이는 활동으로 기록된다 — 마감·확정 같은 사건이 목록에 반영되어야 한다")
    void changeStatus_recordsActivity() {
        ProjectSection section = section();
        LocalDateTime transitionAt = section.getLastActivityAt().plusMinutes(30);

        section.changeStatus(ProjectSectionStatus.SYNTHESIZING, transitionAt);

        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.SYNTHESIZING);
        assertThat(section.getLastActivityAt()).isEqualTo(transitionAt);
    }

    @Test
    @DisplayName("전이가 거부되면 활동도 기록하지 않는다")
    void changeStatus_rejected_doesNotRecordActivity() {
        ProjectSection section = section();
        LocalDateTime initial = section.getLastActivityAt();

        assertThatThrownBy(() ->
                section.changeStatus(ProjectSectionStatus.CONFIRMED, initial.plusHours(1)))
                .isInstanceOf(BusinessException.class);

        assertThat(section.getLastActivityAt()).isEqualTo(initial);
    }

    @Test
    @DisplayName("활동 없는 전이는 상태만 바꾸고 마지막 활동 시각은 건드리지 않는다 — AI·캐스케이드 전이용 (#292)")
    void changeStatusWithoutActivity_doesNotRecordActivity() {
        ProjectSection section = section();
        LocalDateTime initial = section.getLastActivityAt();

        section.changeStatusWithoutActivity(ProjectSectionStatus.SYNTHESIZING);

        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.SYNTHESIZING);
        assertThat(section.getLastActivityAt())
                .as("사람이 만지지 않은 전이라 마지막 활동 시각은 그대로여야 한다")
                .isEqualTo(initial);
    }

    @Test
    @DisplayName("활동 없는 전이도 거부 규칙은 동일하게 적용된다")
    void changeStatusWithoutActivity_rejected_throws() {
        ProjectSection section = section();

        assertThatThrownBy(() ->
                section.changeStatusWithoutActivity(ProjectSectionStatus.CONFIRMED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("overlay 도입 전 레거시 행(컬럼 null)도 기본값으로 읽힌다")
    void legacyRow_readsAsDefaults() {
        ProjectSection section = section();
        ReflectionTestUtils.setField(section, "driftStatus", null);
        ReflectionTestUtils.setField(section, "synthesisStale", null);
        ReflectionTestUtils.setField(section, "confirmedVersion", null);

        assertThat(section.getDriftStatus()).isEqualTo(DriftStatus.NONE);
        assertThat(section.isSynthesisStale()).isFalse();
        assertThat(section.getConfirmedVersion()).isZero();
    }

    @Test
    @DisplayName("드리프트 표시 후 확정하면 NONE 으로 복귀한다 (§6.4·API_SPEC §3.7.6)")
    void drift_marksAndClears() {
        ProjectSection section = section();

        section.markDriftReviewRequired();
        assertThat(section.getDriftStatus()).isEqualTo(DriftStatus.REVIEW_REQUIRED);

        section.clearDrift();
        assertThat(section.getDriftStatus()).isEqualTo(DriftStatus.NONE);
    }

    @Test
    @DisplayName("성공한 사전 검토가 없으면 본문을 수정해도 aiCheckStatus 는 생기지 않는다 (§3.8.4-2)")
    void aiCheckOutdated_withoutHistory_staysNull() {
        ProjectSection section = section();

        section.markAiCheckOutdated();

        assertThat(section.getAiCheckStatus()).isNull();
    }

    @Test
    @DisplayName("CURRENT 검토는 본문 수정 시 OUTDATED 가 되고, 재검토 성공으로 CURRENT 로 돌아온다 (§5.3.3)")
    void aiCheck_currentToOutdatedAndBack() {
        ProjectSection section = section();

        section.bindCurrentAiCheck();
        assertThat(section.getAiCheckStatus()).isEqualTo(AiCheckStatus.CURRENT);

        section.markAiCheckOutdated();
        assertThat(section.getAiCheckStatus()).isEqualTo(AiCheckStatus.OUTDATED);

        section.bindCurrentAiCheck();
        assertThat(section.getAiCheckStatus()).isEqualTo(AiCheckStatus.CURRENT);
    }

    @Test
    @DisplayName("이미 OUTDATED 인 검토는 본문을 또 수정해도 OUTDATED 를 유지한다")
    void aiCheckOutdated_isIdempotent() {
        ProjectSection section = section();
        section.bindCurrentAiCheck();
        section.markAiCheckOutdated();

        section.markAiCheckOutdated();

        assertThat(section.getAiCheckStatus()).isEqualTo(AiCheckStatus.OUTDATED);
    }

    @Test
    @DisplayName("재오픈으로 stale 표시 후 재정리 완료로 해제된다 (§4.5·API_SPEC §3.8.1)")
    void synthesisStale_marksAndClears() {
        ProjectSection section = section();

        section.markSynthesisStale();
        assertThat(section.isSynthesisStale()).isTrue();

        section.clearSynthesisStale();
        assertThat(section.isSynthesisStale()).isFalse();
    }

    @Test
    @DisplayName("의견 수집 재오픈마다 마감 세대가 1씩 증가한다")
    void opinionGateGeneration_advancesOnEveryReopen() {
        ProjectSection section = section();

        section.advanceOpinionGateGeneration();
        section.advanceOpinionGateGeneration();

        assertThat(section.getOpinionGateGeneration()).isEqualTo(2);
    }

    @Test
    @DisplayName("확정 시 확정 본문 버전이 기록된다 — 0 이하 버전은 거부한다")
    void recordConfirmedVersion_storesPositiveVersionOnly() {
        ProjectSection section = section();

        section.recordConfirmedVersion(3);
        assertThat(section.getConfirmedVersion()).isEqualTo(3);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> section.recordConfirmedVersion(0));
    }

    private ProjectSection section() {
        return ProjectSection.builder()
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.COLLECTING)
                .build();
    }
}
