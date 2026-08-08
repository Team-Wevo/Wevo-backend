package com.wevo.backend.project.dto.request;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import java.util.Locale;

/**
 * 프로젝트 목록 검색·필터 조건. (API_SPEC §3.2.2)
 *
 * <p>세 조건은 <b>AND</b> 로 묶이고, {@code null} 인 항목은 조건에서 빠진다. 셋 다 없으면
 * 기존과 똑같이 전체 목록이 나온다(하위 호환).
 *
 * <p>검색 대상은 <b>제목만</b>이다. {@code ideaText}·{@code audience} 까지 넣으면 "무엇을 검색했는지"가
 * 사용자에게 보이지 않아(목록 카드에 제목만 표시된다) 왜 걸렸는지 알 수 없는 결과가 섞인다.
 *
 * @param keyword    제목 부분 일치 키워드. 대소문자를 구분하지 않는다
 * @param resultType 결과물 유형
 * @param status     프로젝트 상태. 보관(ARCHIVED)은 목록에서 항상 제외되므로 지정해도 빈 목록이다
 */
public record ProjectSearchCondition(String keyword, OutputType resultType, ProjectStatus status) {

    public ProjectSearchCondition {
        // 공백만 넣은 검색어는 "검색하지 않음"으로 다룬다 — 그대로 두면 전체가 걸려 필터가 무의미해진다.
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.strip();
    }

    /** 조건이 하나도 없는, 전체 목록을 뜻하는 값. */
    public static ProjectSearchCondition none() {
        return new ProjectSearchCondition(null, null, null);
    }

    /**
     * 이 프로젝트가 조건에 맞는지 판단한다.
     *
     * <p>DB 쿼리가 아니라 메모리에서 거른다. 목록은 사용자당 소수라 페이지네이션도 두지 않기로
     * 확정한 API 이고(§3.2.2), 정렬 기준이 "섹션들의 마지막 활동 시각 중 최대값"이라 어차피 전
     * 멤버십을 읽어야 하기 때문이다. 동적 쿼리를 짜도 읽는 행 수가 줄지 않는다.
     */
    public boolean matches(Project project) {
        return matchesKeyword(project.getTitle())
                && (resultType == null || resultType == project.getResultType())
                && (status == null || status == project.getStatus());
    }

    private boolean matchesKeyword(String title) {
        if (keyword == null) {
            return true;
        }
        if (title == null) {
            return false;
        }
        return title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
