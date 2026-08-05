package com.wevo.backend.export.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FinalOutputFileNamerTest {

    private final FinalOutputFileNamer namer = new FinalOutputFileNamer();

    @Test
    @DisplayName("프로젝트 제목에 형식별 확장자를 붙여 파일명을 만든다")
    void appendsExtensionByFormat() {
        assertThat(fileName("위보 기획", FinalOutputFormat.PLAIN_TEXT)).isEqualTo("위보 기획.txt");
        assertThat(fileName("위보 기획", FinalOutputFormat.MARKDOWN)).isEqualTo("위보 기획.md");
    }

    @Test
    @DisplayName("요청 파일명이 있으면 프로젝트 제목 대신 그것을 쓴다")
    void requestedNameWinsOverProjectTitle() {
        assertThat(namer.fileName("최종 제안서", "위보 기획", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("최종 제안서.txt");
        assertThat(namer.asciiFileName("Final Proposal", "위보 기획", FinalOutputFormat.MARKDOWN))
                .isEqualTo("Final Proposal.md");
    }

    @ParameterizedTest
    @DisplayName("요청 파일명이 비어 있으면 프로젝트 제목으로 떨어진다")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankRequestedNameFallsBackToProjectTitle(String requestedName) {
        assertThat(namer.fileName(requestedName, "위보 기획", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("위보 기획.txt");
    }

    @Test
    @DisplayName("요청 파일명도 제목과 같은 정제를 통과한다 — 사용자 입력이라고 느슨하게 다루지 않는다")
    void requestedNameGoesThroughTheSameSanitize() {
        assertThat(namer.fileName("../../etc/passwd", "위보 기획", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("etc passwd.txt");
    }

    @Test
    @DisplayName("요청 파일명의 개행은 제거된다 — Content-Disposition 헤더가 쪼개지지 않아야 한다")
    void requestedNameCannotInjectHeader() {
        String injected = "제안서\r\nSet-Cookie: session=stolen";

        assertThat(namer.fileName(injected, "위보 기획", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("제안서 Set-Cookie session=stolen.txt")
                .doesNotContain("\r", "\n");
    }

    @Test
    @DisplayName("요청 파일명이 이미 해당 확장자로 끝나면 중복해서 붙이지 않는다")
    void doesNotDuplicateExtension() {
        assertThat(namer.fileName("제안서.txt", null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("제안서.txt");
        // 대소문자를 가리지 않는다
        assertThat(namer.fileName("제안서.MD", null, FinalOutputFormat.MARKDOWN))
                .isEqualTo("제안서.md");
    }

    @Test
    @DisplayName("다른 형식의 확장자는 이름의 일부로 보고 그대로 둔다")
    void keepsUnrelatedExtension() {
        assertThat(namer.fileName("제안서.docx", null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("제안서.docx.txt");
    }

    @Test
    @DisplayName("요청 파일명이 정제 후 비면 대체 이름으로 떨어진다 — 요청을 거부하지 않는다")
    void unusableRequestedNameFallsBackInsteadOfFailing() {
        assertThat(namer.fileName("///", null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("final-output.txt");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("경로 구분자·OS 예약 문자는 공백으로 바꾸고 연속 공백은 하나로 줄인다")
    @CsvSource(delimiter = ';', value = {
            // 경로를 벗어나려는 입력이 파일명에 남지 않아야 한다
            "../../etc/passwd ; etc passwd.txt",
            "a\\b/c            ; a b c.txt",
            "제안서: 1차?       ; 제안서 1차.txt",
            "따옴표<>제목       ; 따옴표 제목.txt",
            "여러   공백        ; 여러 공백.txt"
    })
    void sanitizesIllegalCharacters(String title, String expected) {
        assertThat(fileName(title, FinalOutputFormat.PLAIN_TEXT)).isEqualTo(expected);
    }

    @Test
    @DisplayName("파이프 문자도 파일명에서 걸러낸다")
    void sanitizesPipe() {
        assertThat(fileName("제안서|초안", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("제안서 초안.txt");
    }

    @Test
    @DisplayName("제어문자를 공백으로 바꾼다")
    void sanitizesControlCharacters() {
        assertThat(fileName("줄바꿈\n낀\t제목", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("줄바꿈 낀 제목.txt");
    }

    @Test
    @DisplayName("앞의 마침표를 없앤다 — 유닉스 숨김 파일이 되는 것을 막는다")
    void stripsLeadingDot() {
        assertThat(fileName(".gitignore", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("gitignore.txt");
    }

    @Test
    @DisplayName("뒤의 마침표·공백을 없앤다 — 윈도우에서 허용되지 않는다")
    void stripsTrailingDotAndSpace() {
        assertThat(fileName("제안서...  ", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("제안서.txt");
    }

    @ParameterizedTest
    @DisplayName("제목이 비었거나 정제 후 남는 글자가 없으면 대체 이름을 쓴다")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "...", "///", "\t\n"})
    void fallsBackWhenNothingRemains(String title) {
        assertThat(fileName(title, FinalOutputFormat.MARKDOWN)).isEqualTo("final-output.md");
    }

    @Test
    @DisplayName("파일명이 길면 바이트 예산에 맞춰 자르고, 잘린 끝의 공백도 털어낸다")
    void limitsLength() {
        // 한글은 글자당 3바이트 — `.txt`(4바이트)를 뺀 251바이트 예산에 83글자(249바이트)까지 들어간다.
        String longTitle = "가".repeat(83) + "  꼬리";

        assertThat(fileName(longTitle, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("가".repeat(83) + ".txt");
    }

    @Test
    @DisplayName("ASCII 대체 파일명은 비 ASCII 문자를 걸러낸다")
    void asciiFileNameKeepsOnlyAsciiSafeCharacters() {
        assertThat(asciiFileName("Wevo 기획안 v2", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("Wevo v2.txt");
    }

    @Test
    @DisplayName("제목이 전부 한글이면 ASCII 대체 파일명은 대체 이름으로 떨어진다")
    void asciiFileNameFallsBackForNonAsciiTitle() {
        assertThat(asciiFileName("위보 기획", FinalOutputFormat.MARKDOWN))
                .isEqualTo("final-output.md");
    }

    @Test
    @DisplayName("ASCII 대체 파일명도 표시용 파일명과 같은 정제 규칙을 탄다")
    void asciiFileNameSharesSanitizeRules() {
        // 두 이름이 다른 규칙을 타면 같은 파일이 클라이언트에 따라 다른 이름으로 저장된다.
        assertThat(asciiFileName("../secret/plan", FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("secret plan.txt");
    }

    // 아래 문자들은 반드시 유니코드 이스케이프로 적는다. 소스에 실제 문자를 넣으면 git 이 이 파일을
    // 바이너리로 판정해 diff·리뷰가 불가능해지고, RLO 는 소스 자체를 실제와 다르게 보이게 만든다
    // (trojan source, CVE-2021-42574). 이스케이프는 어휘 분석 단계에서 처리되므로 값은 동일하다.
    // 케이스 이름에도 값을 넣지 않는다 — 리포트·콘솔 출력이 같은 이유로 흐트러진다.
    @ParameterizedTest(name = "[{index}]")
    @DisplayName("눈에 보이지 않는 서식 문자를 제거한다 — 표시 이름 위조를 막는다")
    @ValueSource(strings = {
            "제안\u202E서",  // RLO — 뒤 글자를 거꾸로 보이게 해 확장자를 위조한다
            "제안\u200B서",  // ZWSP
            "제안\u200D서",  // ZWJ
            "제안\uFEFF서"   // BOM
    })
    void removesInvisibleFormatCharacters(String title) {
        String fileName = fileName(title, FinalOutputFormat.PLAIN_TEXT);

        assertThat(fileName).isEqualTo("제안 서.txt");
        assertThat(fileName.codePoints())
                .as("서식 문자가 남으면 안 된다")
                .noneMatch(cp -> Character.getType(cp) == Character.FORMAT);
    }

    @Test
    @DisplayName("바이트로 세도 자르는 위치는 코드포인트 경계다 — 이모지가 반토막 나지 않는다")
    void limitsLengthByCodePointNotChar() {
        // 이모지는 UTF-8 4바이트짜리 글자라 70개면 280바이트로 예산(251)을 넘긴다.
        // 바이트 위치에서 그냥 끊으면 글자 한가운데가 잘려 짝 없는 서로게이트가 남는다.
        String emojiTitle = "🎉".repeat(70);

        String fileName = fileName(emojiTitle, FinalOutputFormat.PLAIN_TEXT);

        assertThat(fileName.chars()
                .filter(c -> Character.isHighSurrogate((char) c) || Character.isLowSurrogate((char) c))
                .count() % 2)
                .as("짝 없는 서로게이트가 남으면 UTF-8 로 옮길 때 ? 가 되어 파일명이 깨진다")
                .isZero();
        assertThat(new String(fileName.getBytes(UTF_8), UTF_8))
                .as("UTF-8 왕복에서 값이 보존돼야 한다")
                .isEqualTo(fileName);
    }

    @Test
    @DisplayName("한글 84자는 잘리고 83자는 그대로 둔다 — 경계는 글자 수가 아니라 바이트다")
    void lengthBoundary() {
        // `.txt` 4바이트를 뺀 251바이트 예산 = 한글 83자(249바이트). 84자는 252바이트로 넘친다.
        assertThat(fileName("가".repeat(83), FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("가".repeat(83) + ".txt");
        assertThat(fileName("가".repeat(84), FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("가".repeat(83) + ".txt");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("어떤 이름이 와도 파일 시스템 상한(255바이트) 안에 들어간다")
    @ValueSource(strings = {"가", "🎉", "a", "제안서 "})
    void neverExceedsFileSystemByteLimit(String unit) {
        // 한글 100자를 글자 수로만 세면 300바이트라 macOS APFS·리눅스 ext4 에서 저장이 실패한다.
        // `fileName` 파라미터에는 길이 검증이 없어 아무 길이나 들어올 수 있으므로 여기서 막아야 한다.
        String longTitle = unit.repeat(500);

        for (FinalOutputFormat format : FinalOutputFormat.values()) {
            assertThat(fileName(longTitle, format).getBytes(UTF_8).length)
                    .as("%s 형식의 파일명 바이트 수", format)
                    .isLessThanOrEqualTo(255);
        }
    }

    @Test
    @DisplayName("예산에서 확장자 몫을 빼므로 확장자가 짧으면 이름이 그만큼 더 들어간다")
    void byteBudgetExcludesExtension() {
        String longTitle = "a".repeat(500);

        // 어느 쪽이든 확장자까지 더해 정확히 상한을 채운다 — 251+4 = 252+3 = 255.
        assertThat(fileName(longTitle, FinalOutputFormat.PLAIN_TEXT)).isEqualTo("a".repeat(251) + ".txt");
        assertThat(fileName(longTitle, FinalOutputFormat.MARKDOWN)).isEqualTo("a".repeat(252) + ".md");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("윈도우 예약 장치명은 대체 이름으로 바꾼다 — 금지 문자가 없어도 저장할 수 없다")
    @ValueSource(strings = {"CON", "con", "NUL", "PRN", "AUX", "COM1", "lpt9", "CON.docx", "  con.  "})
    void replacesWindowsReservedDeviceNames(String requested) {
        // 확장자를 붙여도 예약이 풀리지 않는다 — `CON.txt` 도 윈도우에서는 만들 수 없다.
        assertThat(namer.fileName(requested, null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("final-output.txt");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("장치명과 비슷하기만 한 이름은 그대로 쓴다 — 과하게 막지 않는다")
    @ValueSource(strings = {"CONTRACT", "COM0", "LPT10", "CON 기획", "제안서 CON"})
    void keepsNamesThatOnlyLookReserved(String requested) {
        assertThat(namer.fileName(requested, null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo(requested + ".txt");
    }

    @Test
    @DisplayName("ASCII 대체 파일명이 예약 장치명이 되면 그쪽도 대체 이름으로 떨어진다")
    void asciiFileNameNeverBecomesReservedDeviceName() {
        // 표시 이름은 `CON기획`이라 예약이 아니지만, 비 ASCII 를 떼어내면 `CON` 만 남는다.
        assertThat(namer.fileName("CON기획", null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("CON기획.txt");
        assertThat(namer.asciiFileName("CON기획", null, FinalOutputFormat.PLAIN_TEXT))
                .isEqualTo("final-output.txt");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @DisplayName("확장자 처리 경계")
    @CsvSource(delimiter = ';', value = {
            ".txt        ; txt.txt",       // 확장자만 있으면 앞의 점이 떨어져 이름이 된다
            "t           ; t.txt",         // 확장자보다 짧은 이름
            "제안서 .txt  ; 제안서.txt",     // 확장자 앞 공백
            "제안서..txt  ; 제안서.txt",     // 점 중복
            "제안서.TXT   ; 제안서.txt"      // 대소문자 무시
    })
    void extensionBoundaries(String requested, String expected) {
        assertThat(namer.fileName(requested, null, FinalOutputFormat.PLAIN_TEXT)).isEqualTo(expected);
    }

    @Test
    @DisplayName("NUL 문자를 제거한다")
    void removesNulCharacter() {
        assertThat(fileName("제안\u0000서", FinalOutputFormat.PLAIN_TEXT)).isEqualTo("제안 서.txt");
    }

    @ParameterizedTest
    @DisplayName("ASCII 대체 파일명에는 따옴표·역슬래시가 남지 않는다 — 헤더가 깨지지 않아야 한다")
    @ValueSource(strings = {
            "plan\"; attachment; filename=\"evil",
            "plan\\evil",
            "pl\"an"
    })
    void asciiFileNameNeverBreaksTheQuotedHeader(String requested) {
        // 컨트롤러가 이 값을 filename="..." 안에 그대로 넣으므로 따옴표가 섞이면 헤더가 쪼개진다.
        assertThat(namer.asciiFileName(requested, null, FinalOutputFormat.PLAIN_TEXT))
                .doesNotContain("\"", "\\");
    }

    // --- 헬퍼: 요청 파일명 없이 프로젝트 제목만 쓰는 기본 경로 ---

    private String fileName(String projectTitle, FinalOutputFormat format) {
        return namer.fileName(null, projectTitle, format);
    }

    private String asciiFileName(String projectTitle, FinalOutputFormat format) {
        return namer.asciiFileName(null, projectTitle, format);
    }
}
