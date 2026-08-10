package com.wevo.backend.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.project.service.ProjectMemberRosterQueryService;
import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.review.repository.TeamReviewRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TeamReviewService#evaluateConfirmReviewGate}의 확정 조건 판정 로직(§6.3 조건 2·3) 단위 테스트.
 *
 * <p>두 조건이 같은 팀 검토 목록에서 파생되는지, 만료·해소·1인 예외가 정확히 반영되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TeamReviewServiceTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long SECTION_ID = 10L;

    @Mock
    private TeamReviewRepository teamReviewRepository;
    @Mock
    private ProjectMemberRosterQueryService memberRosterQueryService;

    @InjectMocks
    private TeamReviewService teamReviewService;

    private void givenMembers(long count) {
        given(memberRosterQueryService.countParticipants(PROJECT_ID)).willReturn(count);
    }

    private void givenReviews(TeamReview... reviews) {
        // 탈퇴 검토자 제외는 쿼리가 하므로(§6.1 목록과 같은 집합) 여기서는 남은 검토만 주어진다.
        given(teamReviewRepository.findActiveReviewerReviewsByProjectSectionId(SECTION_ID))
                .willReturn(List.of(reviews));
    }

    private ConfirmReviewGate evaluate() {
        return teamReviewService.evaluateConfirmReviewGate(PROJECT_ID, SECTION_ID);
    }

    @Nested
    @DisplayName("팀원 동의 조건 (§6.3 조건 2·§6.3.1)")
    class MemberApproval {

        @Test
        @DisplayName("1인 프로젝트(멤버 1명)면 검토가 없어도 충족이다 (§6.3.1)")
        void soloProject_alwaysSatisfied() {
            givenMembers(1);
            givenReviews();

            assertThat(evaluate().memberApprovalSatisfied()).isTrue();
        }

        @Test
        @DisplayName("팀원이 있고 현재 본문 기준 APPROVED 가 1명 이상이면 충족")
        void currentApprovalExists_satisfied() {
            givenMembers(3);
            givenReviews(review(TeamReviewStatus.APPROVED, false, false));

            assertThat(evaluate().memberApprovalSatisfied()).isTrue();
        }

        @Test
        @DisplayName("APPROVED 가 본문 수정으로 만료(outdated)됐으면 세지 않아 미충족")
        void onlyOutdatedApproval_notSatisfied() {
            givenMembers(3);
            givenReviews(review(TeamReviewStatus.APPROVED, false, true));

            assertThat(evaluate().memberApprovalSatisfied()).isFalse();
        }

        @Test
        @DisplayName("동의한 팀원이 없으면 미충족")
        void noApproval_notSatisfied() {
            givenMembers(2);
            givenReviews(review(TeamReviewStatus.CHANGES_REQUESTED, false, false));

            assertThat(evaluate().memberApprovalSatisfied()).isFalse();
        }
    }

    @Nested
    @DisplayName("미해결 수정요청 (§6.3 조건 3)")
    class UnresolvedChangeRequest {

        @Test
        @DisplayName("해소되지 않고 만료되지도 않은 수정요청이 있으면 noUnresolved=false")
        void activeChangeRequest_false() {
            givenMembers(2);
            givenReviews(review(TeamReviewStatus.CHANGES_REQUESTED, false, false));

            assertThat(evaluate().noUnresolvedChangeRequest()).isFalse();
        }

        @Test
        @DisplayName("수정요청이 해소(resolved)됐으면 막지 않는다 (§6.1.1)")
        void resolvedChangeRequest_true() {
            givenMembers(2);
            givenReviews(review(TeamReviewStatus.CHANGES_REQUESTED, true, false));

            assertThat(evaluate().noUnresolvedChangeRequest()).isTrue();
        }

        @Test
        @DisplayName("수정요청이 본문 수정으로 만료(outdated)됐으면 막지 않는다")
        void outdatedChangeRequest_true() {
            givenMembers(2);
            givenReviews(review(TeamReviewStatus.CHANGES_REQUESTED, false, true));

            assertThat(evaluate().noUnresolvedChangeRequest()).isTrue();
        }

        @Test
        @DisplayName("APPROVED 만 있으면 미해결 수정요청은 없다")
        void onlyApproved_true() {
            givenMembers(2);
            givenReviews(review(TeamReviewStatus.APPROVED, false, false));

            assertThat(evaluate().noUnresolvedChangeRequest()).isTrue();
        }
    }

    @Test
    @DisplayName("동의 1명 + 미해결 수정요청 1건이면 동의는 충족, 미해결은 false — 한 목록에서 두 조건 파생")
    void bothConditionsFromSingleList() {
        givenMembers(3);
        givenReviews(
                review(TeamReviewStatus.APPROVED, false, false),
                review(TeamReviewStatus.CHANGES_REQUESTED, false, false));

        ConfirmReviewGate gate = evaluate();

        assertThat(gate.memberApprovalSatisfied()).isTrue();
        assertThat(gate.noUnresolvedChangeRequest()).isFalse();
    }

    private TeamReview review(TeamReviewStatus status, boolean resolved, boolean outdated) {
        TeamReview review = TeamReview.builder()
                .status(status)
                .reviewedContentVersion(1)
                .build();
        if (resolved) {
            review.updateResolved(true);
        }
        if (outdated) {
            review.markOutdated();
        }
        return review;
    }
}
