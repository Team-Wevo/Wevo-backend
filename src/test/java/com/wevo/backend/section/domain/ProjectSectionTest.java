package com.wevo.backend.section.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
