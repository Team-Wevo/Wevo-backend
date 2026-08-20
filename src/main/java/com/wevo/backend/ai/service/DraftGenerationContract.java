package com.wevo.backend.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AI 초안 생성의 입력·prompt·schema 버전 계약. */
public final class DraftGenerationContract {

    public static final String PROMPT_VERSION = "draft-generation:v2";
    public static final String SCHEMA_VERSION =
            DraftGenerationOutputDefinition.SCHEMA_ID.trackingValue();

    /** 미답변 GAP 마커 {@code [미확인:GAP-N]}. 검증(OutputValidator)이 존재를 강제한다. */
    private static final Pattern GAP_MARKER = Pattern.compile("\\[미확인:GAP-\\d+]");

    private DraftGenerationContract() {
    }

    public static String unresolvedGapMarker(Long issueId) {
        if (issueId == null || issueId <= 0) {
            throw new IllegalArgumentException("미답변 GAP issue ID는 양수여야 합니다.");
        }
        return "[미확인:GAP-" + issueId + "]";
    }

    /**
     * 검증을 통과한 본문의 미답변 GAP 마커를 화면 표시용으로 변환한다.
     * 마커 자체는 제거하고, 마커 뒤부터 문장이 끝나는 지점(마침표·개행·본문 끝)까지의
     * "확인이 필요한 구절"을 마크다운 굵게({@code **...**})로 감싼다.
     *
     * <p>마커는 위치만 가리키고 구간의 끝을 표시하지 않으므로, 끝 경계는 다음 마침표(포함)
     * 또는 개행/본문 끝으로 근사한다. 이 변환은 검증(OutputValidator)이 마커 존재를 확인한
     * <b>뒤</b>에만 적용해야 한다 — 변환하면 마커가 사라지기 때문이다.
     *
     * @param content 검증을 통과한 AI 초안 본문 (마크다운)
     * @return 마커를 굵게 변환한 표시용 본문. 마커가 없으면 원본을 그대로 반환한다.
     */
    public static String renderUnresolvedGapMarkersAsBold(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        Matcher matcher = GAP_MARKER.matcher(content);
        StringBuilder result = new StringBuilder(content.length());
        int cursor = 0;
        while (matcher.find()) {
            result.append(content, cursor, matcher.start());

            int spanStart = matcher.end();
            while (spanStart < content.length() && content.charAt(spanStart) == ' ') {
                spanStart++;
            }
            int spanEnd = spanStart;
            while (spanEnd < content.length()) {
                char current = content.charAt(spanEnd);
                if (current == '\n') {
                    break;
                }
                if (current == '.') {
                    spanEnd++; // 마침표까지 구절에 포함한다.
                    break;
                }
                spanEnd++;
            }
            String span = content.substring(spanStart, spanEnd).strip();
            if (!span.isEmpty()) {
                result.append("**").append(span).append("**");
            }
            cursor = spanEnd;
        }
        result.append(content, cursor, content.length());
        return result.toString();
    }
}
