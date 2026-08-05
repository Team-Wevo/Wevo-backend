package com.wevo.backend.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 외부 검토 링크의 유효 기간 판정과 상태 전이 규칙. (DB 없이 날짜 로직만 검증)
 *
 * <p>만료를 배치로 훑지 않고 <b>읽는 시점의 날짜</b>로 판정하는 설계라, 경계값(마지막 날 당일)과
 * 종결 사유 보존이 이 클래스의 불변식이다. 통합 테스트는 경로 연결을 보고, 조합 검증은 여기서 한다.
 */
class ReviewLinkTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 6);

    @Nested
    @DisplayName("유효 기간 판정 (isPastDue)")
    class PastDue {

        @Test
        @DisplayName("마지막 날 당일은 아직 지나지 않았다 — 그 날 끝까지 제출을 받는다")
        void lastDayIsNotPastDue() {
            assertThat(link(ReviewLinkStatus.ACTIVE, TODAY).isPastDue(TODAY)).isFalse();
        }

        @Test
        @DisplayName("마지막 날 다음 날부터 지난 것으로 본다")
        void dayAfterLastDayIsPastDue() {
            assertThat(link(ReviewLinkStatus.ACTIVE, TODAY.minusDays(1)).isPastDue(TODAY)).isTrue();
        }

        @Test
        @DisplayName("마지막 날 이전은 지나지 않았다")
        void beforeLastDayIsNotPastDue() {
            assertThat(link(ReviewLinkStatus.ACTIVE, TODAY.plusDays(1)).isPastDue(TODAY)).isFalse();
        }

        @Test
        @DisplayName("기간을 지정하지 않은 링크는 언제 읽어도 지나지 않는다")
        void noExpiryIsNeverPastDue() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, null);

            assertThat(link.isPastDue(TODAY)).isFalse();
            assertThat(link.isPastDue(TODAY.plusYears(100))).isFalse();
        }
    }

    @Nested
    @DisplayName("읽기 시점 상태 (statusAsOf)")
    class StatusAsOf {

        @Test
        @DisplayName("기간이 지난 ACTIVE 링크는 저장된 상태를 정리하지 않았어도 EXPIRED 로 읽힌다")
        void pastDueActiveReadsAsExpired() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, TODAY.minusDays(1));

            assertThat(link.statusAsOf(TODAY)).isEqualTo(ReviewLinkStatus.EXPIRED);
            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.ACTIVE); //읽기는 상태를 바꾸지 않는다
        }

        @Test
        @DisplayName("마지막 날 당일의 ACTIVE 링크는 그대로 ACTIVE 로 읽힌다")
        void lastDayActiveStaysActive() {
            assertThat(link(ReviewLinkStatus.ACTIVE, TODAY).statusAsOf(TODAY))
                    .isEqualTo(ReviewLinkStatus.ACTIVE);
        }

        @Test
        @DisplayName("먼저 끝난 링크의 종결 사유는 기간이 지나도 EXPIRED 로 덮이지 않는다")
        void earlierTerminalReasonWins() {
            LocalDate pastDue = TODAY.minusDays(1);

            assertThat(link(ReviewLinkStatus.OUTDATED, pastDue).statusAsOf(TODAY))
                    .isEqualTo(ReviewLinkStatus.OUTDATED);
            assertThat(link(ReviewLinkStatus.CLOSED, pastDue).statusAsOf(TODAY))
                    .isEqualTo(ReviewLinkStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("상태 정리 (expireIfPastDue)")
    class ExpireIfPastDue {

        @Test
        @DisplayName("기간이 지난 ACTIVE 링크만 EXPIRED 로 정리한다")
        void expiresPastDueActive() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, TODAY.minusDays(1));

            assertThat(link.expireIfPastDue(TODAY)).isTrue();
            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.EXPIRED);
        }

        @Test
        @DisplayName("마지막 날 당일에는 정리하지 않는다 — 하루를 먼저 닫아버리지 않도록")
        void keepsActiveOnLastDay() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, TODAY);

            assertThat(link.expireIfPastDue(TODAY)).isFalse();
            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.ACTIVE);
        }

        @Test
        @DisplayName("기간 미지정 링크는 정리 대상이 아니다")
        void keepsLinkWithoutExpiry() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, null);

            assertThat(link.expireIfPastDue(TODAY)).isFalse();
            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.ACTIVE);
        }

        @Test
        @DisplayName("이미 끝난 링크는 기간이 지났어도 종결 사유를 유지한다")
        void keepsTerminalReason() {
            ReviewLink outdated = link(ReviewLinkStatus.OUTDATED, TODAY.minusDays(1));
            ReviewLink closed = link(ReviewLinkStatus.CLOSED, TODAY.minusDays(1));

            assertThat(outdated.expireIfPastDue(TODAY)).isFalse();
            assertThat(closed.expireIfPastDue(TODAY)).isFalse();
            assertThat(outdated.getStatus()).isEqualTo(ReviewLinkStatus.OUTDATED);
            assertThat(closed.getStatus()).isEqualTo(ReviewLinkStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("본문 수정 만료 (markOutdated)")
    class MarkOutdated {

        @Test
        @DisplayName("ACTIVE 링크만 OUTDATED 로 만료된다")
        void marksActiveOutdated() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, TODAY.plusDays(1));
            link.markOutdated();

            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.OUTDATED);
        }

        @Test
        @DisplayName("이미 EXPIRED 인 링크는 본문이 수정돼도 OUTDATED 로 덮이지 않는다")
        void doesNotOverwriteExpired() {
            ReviewLink link = link(ReviewLinkStatus.EXPIRED, TODAY.minusDays(1));
            link.markOutdated();

            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.EXPIRED);
        }
    }

    @Nested
    @DisplayName("수동 종료 (close)")
    class Close {

        @Test
        @DisplayName("기간 안의 ACTIVE 링크는 CLOSED 로 종료된다")
        void closesActiveLink() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, TODAY);
            link.close(TODAY);

            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.CLOSED);
        }

        @Test
        @DisplayName("기간이 지난 ACTIVE 링크는 R014 로 거절하고 상태를 건드리지 않는다")
        void rejectsPastDueLink() {
            ReviewLink link = link(ReviewLinkStatus.ACTIVE, TODAY.minusDays(1));

            assertThatThrownBy(() -> link.close(TODAY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_EXPIRED);
            // 롤백에 기대지 않고 거절 경로 자체가 상태를 유지해야 한다
            assertThat(link.getStatus()).isEqualTo(ReviewLinkStatus.ACTIVE);
        }

        @Test
        @DisplayName("이미 끝난 링크는 종결 사유별로 다른 코드로 거절한다")
        void rejectsTerminalLinkByReason() {
            assertThatThrownBy(() -> link(ReviewLinkStatus.CLOSED, null).close(TODAY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_CLOSED);

            assertThatThrownBy(() -> link(ReviewLinkStatus.OUTDATED, null).close(TODAY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_OUTDATED);

            assertThatThrownBy(() -> link(ReviewLinkStatus.EXPIRED, TODAY.minusDays(1)).close(TODAY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_LINK_CLOSE_ALREADY_EXPIRED);
        }
    }

    /** 날짜 로직만 검증하므로 연관 엔티티·스냅샷은 채우지 않는다. */
    private static ReviewLink link(ReviewLinkStatus status, LocalDate expiresOn) {
        return ReviewLink.builder()
                .status(status)
                .expiresOn(expiresOn)
                .build();
    }
}
