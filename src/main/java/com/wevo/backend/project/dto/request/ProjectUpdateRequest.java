package com.wevo.backend.project.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 기본 정보 수정 요청. (API_SPEC §3.2.10)
 *
 * <p>부분 수정이라 두 필드 모두 선택이다. 필드가 <b>본문에 없거나 {@code null} 이면 기존값을
 * 유지</b>하고, 값이 있으면 교체한다.
 *
 * <p>수정 대상이 이름·설명 둘뿐인 이유는 {@code ideaText}·{@code audience}·{@code resultType} 이
 * 프로젝트 생성 시점에 섹션의 핵심 질문·작성 가이드를 만든 AI 입력이기 때문이다(정책서 §2.4.1).
 * 값만 바꾸면 이미 생성된 가이드가 낡은 아이디어 기준으로 남는다.
 *
 * @param title       프로젝트 이름 (선택 — 보내면 공백일 수 없고 최대 200자)
 * @param description 프로젝트 설명 (선택 — 최대 2,000자. <b>빈 문자열은 값 지우기</b>)
 */
public record ProjectUpdateRequest(
        @Pattern(regexp = ".+", message = "공백일 수 없습니다")
        @Size(max = 200)
        String title,

        @Size(max = 2000)
        String description
) {

    /**
     * 앞뒤 공백을 제거해 검증·저장이 같은 값을 보게 한다. ({@code strip()} 은 전각 공백 등
     * 유니코드 공백까지 처리)
     *
     * <p>{@code title} 은 정리 결과가 빈 문자열이면 {@code @Pattern} 에서 걸려 {@code C001} 이 된다.
     * {@code description} 은 빈 문자열이 "값 지우기"라는 정상 입력이므로 검증에서 막지 않는다.
     */
    public ProjectUpdateRequest {
        if (title != null) {
            title = title.strip();
        }
        if (description != null) {
            description = description.strip();
        }
    }
}
