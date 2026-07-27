package com.wevo.backend.ai.service;

/**
 * AI 의견 정리 기능의 버전·한도 계약. (§5.1.7 쟁점 상한, §3.8.1)
 *
 * <p>버전 문자열은 AI 작업 멱등키와 감사 로그에 함께 묶인다 — 프롬프트나 출력 스키마가 바뀌면
 * 값을 올려 이전 결과와 구분한다. 프롬프트 정의의 tracking version과 반드시 일치시킨다.
 */
public final class SynthesisContract {

    /** 입력 원천 버전 — 의견 정리 입력 구성 규칙이 바뀌면 올린다. */
    public static final String SOURCE_VERSION = "opinion-synthesis-source:v1";

    /**
     * 프롬프트 버전 — 렌더링된 프롬프트의 tracking version({@code promptName:vN})과 <b>정확히</b>
     * 일치해야 한다({@code AiInvocationService.invokeStructured}가 검증). 프롬프트 리소스가
     * {@code prompts/ai/opinion-synthesis/v1/}이므로 값도 그에 맞춘다.
     */
    public static final String PROMPT_VERSION = "opinion-synthesis:v1";

    /** 구조화 출력 스키마 버전 — {@code OutputSchemaId}의 tracking value와 맞춘다. */
    public static final String SCHEMA_VERSION = "opinion-synthesis:v1";

    /** 한 세트의 전체 쟁점 상한 (§5.1.7). */
    public static final int MAX_TOTAL_ISSUES = 4;

    /** CONFLICT 쟁점 상한 (§5.1.7). */
    public static final int MAX_CONFLICT_ISSUES = 3;

    /** GAP 쟁점 상한 (§5.1.7). */
    public static final int MAX_GAP_ISSUES = 2;

    private SynthesisContract() {
    }
}
