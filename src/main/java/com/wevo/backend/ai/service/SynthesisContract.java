package com.wevo.backend.ai.service;

/**
 * AI 의견 정리 기능의 입력·prompt·schema 버전 계약. (§3.8.1)
 *
 * <p>버전 문자열은 AI 작업 멱등키와 감사 로그에 함께 묶인다 — 프롬프트나 출력 스키마가 바뀌면
 * 값을 올려 이전 결과와 구분한다. 프롬프트 정의의 tracking version과 반드시 일치시킨다.
 */
public final class SynthesisContract {

    /** 입력 원천 버전 — 의견 정리 입력 구성 규칙이 바뀌면 올린다. */
    public static final String SOURCE_VERSION = "opinion-synthesis-source:v2";

    /**
     * 프롬프트 버전 — 렌더링된 프롬프트의 tracking version({@code promptName:vN})과 <b>정확히</b>
     * 일치해야 한다({@code AiInvocationService.invokeStructured}가 검증). 프롬프트 리소스가
     * {@code prompts/ai/opinion-synthesis/v3/}이므로 값도 그에 맞춘다.
     *
     * <p>v3 은 CONFLICT 쟁점의 description·question·options 작성 품질 지침을 담아, 상충 의견이
     * 엉성한 문구로 정리되던 문제를 개선한 버전이다. 출력 스키마는 v2 와 동일하다({@link #SCHEMA_VERSION}).
     */
    public static final String PROMPT_VERSION = "opinion-synthesis:v3";

    /** 구조화 출력 스키마 버전 — {@code OutputSchemaId}의 tracking value와 맞춘다. */
    public static final String SCHEMA_VERSION = "opinion-synthesis-output:v2";

    private SynthesisContract() {
    }
}
