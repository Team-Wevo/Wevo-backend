package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DraftGenerationContractTest {

    @Test
    @DisplayName("마커를 제거하고 뒤 문장을 마침표까지 굵게 감싼다")
    void boldsClauseUpToPeriod() {
        String content = "이에 따라 [미확인:GAP-125] 부스 구성과 운영 내용이 미확인 상태입니다. 다음 문장은 그대로.";

        String result = DraftGenerationContract.renderUnresolvedGapMarkersAsBold(content);

        assertThat(result)
                .isEqualTo("이에 따라 **부스 구성과 운영 내용이 미확인 상태입니다.** 다음 문장은 그대로.");
    }

    @Test
    @DisplayName("마커가 줄 끝에 있으면 개행 전까지 굵게 감싼다")
    void boldsToLineEndWhenNoPeriod() {
        String content = "미확인 항목: [미확인:GAP-7] 세부 일정 미정\n다음 줄";

        String result = DraftGenerationContract.renderUnresolvedGapMarkersAsBold(content);

        assertThat(result).isEqualTo("미확인 항목: **세부 일정 미정**\n다음 줄");
    }

    @Test
    @DisplayName("여러 마커를 각각 독립적으로 변환한다")
    void transformsMultipleMarkers() {
        String content = "[미확인:GAP-1] 첫째 미정입니다. 사이 문장. [미확인:GAP-2] 둘째 미정입니다.";

        String result = DraftGenerationContract.renderUnresolvedGapMarkersAsBold(content);

        assertThat(result)
                .isEqualTo("**첫째 미정입니다.** 사이 문장. **둘째 미정입니다.**");
    }

    @Test
    @DisplayName("마커가 없으면 본문을 그대로 반환한다")
    void returnsContentUnchangedWhenNoMarker() {
        String content = "부산에서 부스를 운영하기 위한 제안입니다. 구성은 확정되었습니다.";

        String result = DraftGenerationContract.renderUnresolvedGapMarkersAsBold(content);

        assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("마커 뒤에 내용이 없으면 마커만 제거한다")
    void dropsMarkerWhenNothingFollows() {
        String content = "확정해야 합니다. [미확인:GAP-9]";

        String result = DraftGenerationContract.renderUnresolvedGapMarkersAsBold(content);

        assertThat(result).isEqualTo("확정해야 합니다. ");
    }

    @Test
    @DisplayName("null·공백 본문은 그대로 반환한다")
    void returnsBlankAsIs() {
        assertThat(DraftGenerationContract.renderUnresolvedGapMarkersAsBold(null)).isNull();
        assertThat(DraftGenerationContract.renderUnresolvedGapMarkersAsBold("  ")).isEqualTo("  ");
    }
}
