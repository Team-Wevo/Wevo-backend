package com.wevo.backend.opinion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionCollectionState;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.project.service.ProjectMemberSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 섹션의 의견 수집 현황. (제출 N/M + 멤버별 진행 상태 — 정책서 §4.5)
 *
 * <p>팀장이 수집 마감 전에 "몇 명이 아직 안 냈는지"를 확인하는 것이 1차 용도다
 * (§4.5 — "미제출 멤버가 있으면 팀장에게 'N명 작성 중' 경고 후 마감 진행").
 * 팀원에게도 같은 현황을 보여 수집이 어디까지 왔는지 공유한다.
 *
 * <p><b>분모 M({@code totalMembers}) 은 OWNER 를 포함한 프로젝트 멤버 전원이다.</b>
 * 팀 검토(§6.1)가 팀장을 분모에서 빼는 것과 다르다 — 검토는 팀장이 검토자가 아니라 확정 실행자지만,
 * 의견은 팀장도 작성·제출하는 참여자이기 때문이다(의견 API 가 모두 참여자 권한으로 열려 있다).
 * 두 화면의 분모가 다른 것은 <b>의도된 차이</b>이므로 한쪽에 맞춰 통일하지 않는다.
 *
 * <p><b>본문은 담지 않는다</b> — 이 응답은 진행 상태만 노출하므로, 제출 전 사용자에게 남의 의견
 * 내용을 감추는 공개 게이트(§4.3 베끼기 방지)와 충돌하지 않는다. 의견 본문이 필요하면
 * 게이트가 적용된 제출 의견 목록(API_SPEC §3.4.4)을 쓴다.
 *
 * @param collectionOpen  수집 게이트가 열려 있는지 — {@code sectionStatus == COLLECTING} 에서 파생(§4.5).
 *                        닫혀 있으면 미제출 인원이 남아 있어도 더 이상 제출을 받지 않는다
 * @param totalMembers    수집 대상 인원 M (OWNER 포함 프로젝트 멤버 전원)
 * @param submittedCount  제출을 마친 인원 = <b>섹션에 제출된 의견 수</b>
 *                        (한 멤버는 섹션당 의견 하나이므로 두 값이 같다)
 * @param draftingCount   임시저장만 있고 제출하지 않은 인원 (§4.5 "N명 작성 중")
 * @param notStartedCount 의견을 아직 만들지 않은 인원
 * @param pendingCount    미제출 인원 = {@code draftingCount + notStartedCount}.
 *                        마감 경고 문구가 쓰는 값이라 클라이언트가 더하지 않도록 서버가 계산해 내린다
 * @param items           멤버별 진행 상태 (OWNER 우선 → 참여 시각 오름차순)
 */
@Schema(requiredProperties = {
        "collectionOpen", "totalMembers", "submittedCount", "draftingCount",
        "notStartedCount", "pendingCount", "items"
})
public record OpinionCollectionStatusResponse(
        boolean collectionOpen,
        long totalMembers,
        long submittedCount,
        long draftingCount,
        long notStartedCount,
        long pendingCount,
        List<MemberCollectionStateResponse> items
) {

    /**
     * 멤버 한 명의 수집 진행 상태.
     *
     * @param userId                사용자 ID
     * @param name                  표시 이름
     * @param profileImageUrl       프로필 이미지 URL — <b>nullable</b> (없으면 키 생략)
     * @param state                 진행 상태 (파생 — 저장되지 않는다)
     * @param hasUnsubmittedChanges 제출 후 재편집으로 작업본이 제출본과 달라졌는지(§4.1).
     *                              {@code state != SUBMITTED} 면 항상 {@code false}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"userId", "name", "state", "hasUnsubmittedChanges"})
    public record MemberCollectionStateResponse(
            Long userId,
            String name,
            String profileImageUrl,
            OpinionCollectionState state,
            boolean hasUnsubmittedChanges
    ) {

        private static MemberCollectionStateResponse of(ProjectMemberSummary member, Opinion opinion) {
            return new MemberCollectionStateResponse(
                    member.userId(),
                    member.name(),
                    member.profileImageUrl(),
                    stateOf(opinion),
                    opinion != null && opinion.hasUnsubmittedChanges());
        }

        private static OpinionCollectionState stateOf(Opinion opinion) {
            if (opinion == null) {
                return OpinionCollectionState.NOT_STARTED;
            }
            return opinion.getStatus() == OpinionStatus.SUBMITTED
                    ? OpinionCollectionState.SUBMITTED
                    : OpinionCollectionState.DRAFTING;
        }
    }

    /**
     * 멤버 로스터를 기준으로 현황을 구성한다.
     *
     * <p><b>기준은 항상 로스터</b>다 — 의견 쪽에서 출발하지 않는다. 의견이 없는 멤버까지
     * {@code NOT_STARTED} 로 세어야 분모 M 과 세 상태의 합이 맞기 때문이다.
     * (MVP 에는 멤버 탈퇴·강제 제외가 없어 로스터에 없는 작성자의 의견은 생기지 않는다.)
     *
     * @param collectionOpen   수집 게이트 개방 여부
     * @param roster           프로젝트 멤버 전원 (표시 순서가 그대로 {@code items} 순서가 된다)
     * @param opinionByAuthorId 작성자 ID → 그 멤버의 의견 (없는 멤버는 키 자체가 없다)
     */
    public static OpinionCollectionStatusResponse of(boolean collectionOpen,
                                                     List<ProjectMemberSummary> roster,
                                                     Map<Long, Opinion> opinionByAuthorId) {
        List<MemberCollectionStateResponse> items = roster.stream()
                .map(member -> MemberCollectionStateResponse.of(
                        member, opinionByAuthorId.get(member.userId())))
                .toList();

        long submittedCount = count(items, OpinionCollectionState.SUBMITTED);
        long draftingCount = count(items, OpinionCollectionState.DRAFTING);
        long notStartedCount = count(items, OpinionCollectionState.NOT_STARTED);

        return new OpinionCollectionStatusResponse(
                collectionOpen,
                items.size(),
                submittedCount,
                draftingCount,
                notStartedCount,
                draftingCount + notStartedCount,
                items);
    }

    private static long count(List<MemberCollectionStateResponse> items, OpinionCollectionState state) {
        return items.stream().filter(item -> item.state() == state).count();
    }
}
