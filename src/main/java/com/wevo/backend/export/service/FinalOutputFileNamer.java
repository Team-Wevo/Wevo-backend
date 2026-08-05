package com.wevo.backend.export.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
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
 *   <li>길이는 확장자를 포함해 {@value #MAX_FILE_NAME_BYTES} <b>바이트</b>(UTF-8)로 제한한다
 *       — 파일 시스템의 파일명 길이 상한이 바이트 기준이다</li>
 *   <li>윈도우 예약 장치명({@code CON}, {@code NUL}, {@code COM1} …)이면 대체 이름으로 바꾼다</li>
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

    /**
     * 윈도우가 <b>이름 자체를 금지</b>하는 예약 장치명. (대소문자 무시)
     *
     * <p>금지 문자를 다 걸러내도 {@code CON}·{@code NUL} 같은 이름은 윈도우에서 저장이 되지 않는다.
     * 확장자를 붙여도 마찬가지라 {@code CON.txt} 도 만들 수 없다 — 첫 마침표 앞부분이 장치명이면
     * 전부 해당한다.
     */
    private static final Set<String> WINDOWS_RESERVED_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    /** 정제 후 남는 글자가 없을 때 쓰는 이름. */
    private static final String FALLBACK_BASE_NAME = "final-output";

    /**
     * 확장자를 <b>포함한</b> 파일명 길이 상한. (UTF-8 바이트)
     *
     * <p>주요 파일 시스템의 상한은 글자 수가 아니라 바이트다 — macOS APFS·리눅스 ext4 모두
     * 255바이트다. 글자 수로 세면 한글(글자당 3바이트)·이모지(4바이트) 이름이 상한을 훌쩍 넘겨
     * 저장이 실패한다. (윈도우 NTFS 는 255 UTF-16 단위인데, 어떤 문자든 UTF-16 단위 수가 UTF-8
     * 바이트 수를 넘지 않으므로 이 예산이 NTFS 도 함께 만족시킨다)
     */
    private static final int MAX_FILE_NAME_BYTES = 255;

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
        // 비 ASCII 를 떼어내다 예약 장치명이 새로 만들어질 수 있다. (`CON기획` → `CON`)
        return withExtension(isUnusableBaseName(ascii) ? FALLBACK_BASE_NAME : ascii, format);
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
                format), format);

        return isUnusableBaseName(cleaned) ? FALLBACK_BASE_NAME : cleaned;
    }

    /** 정제 결과를 그대로 쓸 수 없는 경우 — 남는 글자가 없거나, 윈도우 예약 장치명이다. */
    private boolean isUnusableBaseName(String baseName) {
        return baseName.isEmpty() || isReservedDeviceName(baseName);
    }

    /**
     * 윈도우 예약 장치명인지 본다. (대소문자 무시)
     *
     * <p>확장자를 붙여도 예약이 풀리지 않으므로 <b>첫 마침표 앞부분</b>으로 판단한다 —
     * {@code CON.docx} 는 우리가 {@code CON.docx.txt} 로 만들어도 윈도우에서 저장되지 않는다.
     * 로케일에 따라 대문자 변환 결과가 달라지지 않도록 {@link Locale#ROOT} 로 비교한다.
     */
    private boolean isReservedDeviceName(String baseName) {
        int dot = baseName.indexOf('.');
        String deviceCandidate = dot < 0 ? baseName : baseName.substring(0, dot);
        return WINDOWS_RESERVED_DEVICE_NAMES.contains(deviceCandidate.toUpperCase(Locale.ROOT));
    }

    /**
     * 확장자까지 더한 파일명이 {@value #MAX_FILE_NAME_BYTES} 바이트를 넘지 않도록 자른다.
     *
     * <p><b>길이는 UTF-8 바이트로 세고, 자르는 위치는 코드포인트 경계에 맞춘다.</b>
     * 파일 시스템 상한이 바이트 기준이라 글자 수로 세면 한글·이모지 이름이 상한을 넘는다.
     * 반대로 바이트 위치에서 그냥 끊으면 글자 한가운데가 잘려 이름이 깨지므로, 한 코드포인트씩
     * 예산을 채워가다 넘치기 직전에 멈춘다. (이모지는 {@code char} 두 개짜리 글자라 이 경계를
     * 지켜야 짝 없는 서로게이트가 남지 않는다)
     */
    private String limitLength(String value, FinalOutputFormat format) {
        int budget = MAX_FILE_NAME_BYTES - utf8Length("." + format.getFileExtension());
        if (utf8Length(value) <= budget) {
            return value;
        }

        int end = 0;
        int used = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int size = utf8Length(codePoint);
            if (used + size > budget) {
                break;
            }
            used += size;
            end += Character.charCount(codePoint);
        }

        // 자르고 나서 끝에 남은 공백·마침표를 다시 제거한다.
        return trimEdges(value.substring(0, end));
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** 코드포인트 하나의 UTF-8 바이트 수. */
    private int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
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
