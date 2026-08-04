package com.wevo.backend.export.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 완성본 다운로드 파일명을 만든다.
 *
 * <p>파일명의 뼈대는 <b>사용자가 지정한 이름</b>이고, 지정하지 않으면 <b>프로젝트 제목</b>이다.
 * 둘 다 사용자가 자유롭게 입력하는 값이라 그대로 파일명에 쓸 수 없다 — 경로 구분자가 섞이면 저장
 * 경로가 흔들리고({@code ../}), 운영체제가 금지하는 문자가 있으면 저장 자체가 실패하며, 개행이
 * 남으면 {@code Content-Disposition} 헤더가 쪼개진다. 그래서 <b>정제(sanitize)</b> 를 거친 뒤에만 쓴다.
 *
 * <p><b>두 출처는 같은 정제를 통과한다.</b> 사용자 입력만 느슨하게 다루면 그쪽이 곧 구멍이 된다.
 *
 * <h2>정제 규칙</h2>
 * <ul>
 *   <li>경로 구분자({@code / \})·OS 예약 문자({@code : * ? " < > |})·제어문자는 공백으로 바꾼다</li>
 *   <li>연속 공백은 하나로 줄이고 앞뒤 공백을 없앤다</li>
 *   <li>앞의 마침표는 없앤다 — 유닉스에서 숨김 파일이 된다</li>
 *   <li>뒤의 마침표·공백은 없앤다 — 윈도우에서 허용되지 않는다</li>
 *   <li>이미 해당 형식의 확장자로 끝나면 떼어낸다 — {@code 제안서.txt.txt} 방지</li>
 *   <li>길이는 {@value #MAX_BASE_LENGTH} 자로 제한한다 (파일 시스템의 파일명 길이 상한 대비)</li>
 *   <li>남는 것이 없으면 {@value #FALLBACK_BASE_NAME} 을 쓴다</li>
 * </ul>
 *
 * <p><b>정제는 거부가 아니다</b> — 쓸 수 없는 이름이 와도 요청을 실패시키지 않고 안전한 이름으로
 * 바꿔 내보낸다. 파일명 때문에 완성본을 못 받는 상황을 만들지 않기 위함이다.
 *
 * <p><b>ASCII 대체 파일명</b>({@link #asciiFileName})은 {@code Content-Disposition} 의
 * {@code filename*}(RFC 5987)을 해석하지 못하는 옛 클라이언트를 위한 값이다. 한글 제목은 ASCII 로
 * 옮길 방법이 없으므로 남는 글자가 없으면 역시 {@value #FALLBACK_BASE_NAME} 으로 떨어진다.
 *
 */
@Component
public class FinalOutputFileNamer {

    /**
     * 파일명에 쓸 수 없는 문자 — 경로 구분자, OS 예약 문자, 제어문자, <b>서식 문자</b>.
     *
     * <p>서식 문자({@code \p{Cf}})까지 막는 이유: {@code U+202E}(RLO) 같은 양방향 제어 문자가 남으면
     * 파일 탐색기에 <b>실제와 다른 이름으로 보인다</b>. 눈에 보이지 않으면서 표시만 바꾸는 문자는
     * 파일명에 필요하지 않으므로 통째로 막는다. (ZWSP·ZWJ·BOM 도 같은 분류다)
     */
    private static final Pattern ILLEGAL_CHARACTERS =
            Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /** ASCII 대체 파일명에 남길 문자 외 전부. */
    private static final Pattern NON_ASCII_SAFE = Pattern.compile("[^A-Za-z0-9 ._-]");

    /** 정제 후 남는 글자가 없을 때 쓰는 이름. */
    private static final String FALLBACK_BASE_NAME = "final-output";

    /** 확장자를 제외한 파일명 길이 상한. (글자 수 = 코드포인트 기준) */
    private static final int MAX_BASE_LENGTH = 100;

    /**
     * 사용자에게 보일 파일명을 만든다. (한글 등 비 ASCII 문자 포함 가능)
     *
     * @param requestedName 사용자가 지정한 파일명 — 없으면(`null`·공백) 프로젝트 제목을 쓴다
     * @param projectTitle  프로젝트 제목 — {@code null}·공백이어도 안전하게 대체 이름으로 떨어진다
     */
    public String fileName(String requestedName, String projectTitle, FinalOutputFormat format) {
        return withExtension(baseName(requestedName, projectTitle, format), format);
    }

    /**
     * {@code filename*} 을 해석하지 못하는 클라이언트를 위한 ASCII 전용 파일명을 만든다.
     *
     * <p>{@link #fileName} 과 같은 정제를 거친 뒤 ASCII 안전 문자만 남긴다 — 두 이름이 서로 다른
     * 규칙을 타면 같은 파일이 클라이언트에 따라 다른 이름으로 저장된다.
     */
    public String asciiFileName(String requestedName, String projectTitle, FinalOutputFormat format) {
        String ascii = trimEdges(collapseWhitespace(NON_ASCII_SAFE
                .matcher(baseName(requestedName, projectTitle, format)).replaceAll(" ")));
        return withExtension(ascii.isEmpty() ? FALLBACK_BASE_NAME : ascii, format);
    }

    private String withExtension(String baseName, FinalOutputFormat format) {
        return baseName + "." + format.getFileExtension();
    }

    /**
     * <p><b>사용자 지정 이름이 있으면 그것이 우선</b>이고, 없을 때만 프로젝트 제목으로 한다.
     * 어느 쪽이든 <b>같은 정제를 통과</b>한다. 
     */
    private String baseName(String requestedName, String projectTitle, FinalOutputFormat format) {
        String source = isBlank(requestedName) ? projectTitle : requestedName;
        if (source == null) {
            return FALLBACK_BASE_NAME;
        }

        String cleaned = limitLength(stripDuplicateExtension(
                trimEdges(collapseWhitespace(ILLEGAL_CHARACTERS.matcher(source).replaceAll(" "))),
                format));

        return cleaned.isEmpty() ? FALLBACK_BASE_NAME : cleaned;
    }

    /**
     * 파일명을 {@value #MAX_BASE_LENGTH} <b>글자</b>로 제한한다.
     *
     * <p><b>{@code char} 가 아니라 코드포인트 단위로 센다.</b> {@code String.length()} 기준으로 자르면
     * 이모지처럼 {@code char} 두 개로 이뤄진 글자의 한가운데가 잘려, 짝 없는 서로게이트가 남는다.
     * 그 값은 UTF-8 로 옮길 때 {@code ?} 가 되어 파일명이 깨진다.
     */
    private String limitLength(String value) {
        if (value.codePointCount(0, value.length()) <= MAX_BASE_LENGTH) {
            return value;
        }

        // 자르고 나서 끝에 남은 공백·마침표를 다시 제거한다.
        return trimEdges(value.substring(0, value.offsetByCodePoints(0, MAX_BASE_LENGTH)));
    }

    /**
     * 이름이 이미 해당 형식의 확장자로 끝나면 떼어낸다. (대소문자 무시)
     *
     * <p>사용자가 {@code 제안서.txt} 라고 적었을 때 {@code 제안서.txt.txt} 가 되는 것을 막는다.
     * 다른 확장자({@code 제안서.docx})는 건드리지 않는다 — 사용자가 이름의 일부로 쓴 것일 수 있고,
     * 실제 확장자는 어차피 뒤에 붙는다.
     */
    private String stripDuplicateExtension(String value, FinalOutputFormat format) {
        String suffix = "." + format.getFileExtension();
        return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length())
                ? trimEdges(value.substring(0, value.length() - suffix.length()))
                : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String collapseWhitespace(String value) {
        return WHITESPACE_RUN.matcher(value).replaceAll(" ");
    }

    /** 앞의 마침표와 뒤의 마침표·공백을 없앤다. */
    private String trimEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '.' || value.charAt(start) == ' ')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == ' ')) {
            end--;
        }
        return value.substring(start, end);
    }
}
