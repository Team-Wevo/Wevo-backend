package com.wevo.backend.export.service;

import com.wevo.backend.export.dto.response.FinalOutputResponse.SectionOutput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 완성본 문자열 조립 규칙 검증.
 *
 * <p>복사본과 이후 추가될 다운로드 파일이 같은 결과를 내야 하므로, 규칙을 문자열 단위로 고정한다.
 */
class FinalOutputFormatterTest {

    private final FinalOutputFormatter formatter = new FinalOutputFormatter();

    private static final List<SectionOutput> SECTIONS = List.of(
            new SectionOutput(1, "제안 배경", "최근 대학생의 장학금 수요가 늘고 있다."),
            new SectionOutput(2, "문제 및 필요성", "학생은 여러 사이트를 오가야 한다."));

    @Test
    @DisplayName("일반 텍스트는 마크다운 기호 없이 제목·번호·빈 줄로만 구조를 만든다")
    void formatsPlainText() {
        String result = formatter.format("장학금 매칭 서비스 제안서", SECTIONS, FinalOutputFormat.PLAIN_TEXT);

        assertThat(result).isEqualTo("""
                장학금 매칭 서비스 제안서

                01. 제안 배경

                최근 대학생의 장학금 수요가 늘고 있다.

                02. 문제 및 필요성

                학생은 여러 사이트를 오가야 한다.""");
    }

    @Test
    @DisplayName("마크다운은 프로젝트 제목에 #, 섹션 제목에 ## 를 붙인다")
    void formatsMarkdown() {
        String result = formatter.format("장학금 매칭 서비스 제안서", SECTIONS, FinalOutputFormat.MARKDOWN);

        assertThat(result).isEqualTo("""
                # 장학금 매칭 서비스 제안서

                ## 01. 제안 배경

                최근 대학생의 장학금 수요가 늘고 있다.

                ## 02. 문제 및 필요성

                학생은 여러 사이트를 오가야 한다.""");
    }

    @Test
    @DisplayName("섹션 번호는 저장된 순서를 두 자리로 채운다 (화면 표기와 일치)")
    void padsSectionNumberToTwoDigits() {
        String result = formatter.format("제목",
                List.of(new SectionOutput(9, "아홉", "본문"), new SectionOutput(10, "열", "본문")),
                FinalOutputFormat.PLAIN_TEXT);

        assertThat(result).contains("09. 아홉").contains("10. 열");
    }

    @Test
    @DisplayName("본문 앞뒤 공백은 제거하고 내부 줄바꿈·빈 줄은 보존한다")
    void trimsOuterWhitespaceButKeepsParagraphs() {
        String result = formatter.format("제목",
                List.of(new SectionOutput(1, "섹션", "\n  첫 문단이다.\n\n둘째 문단이다.  \n")),
                FinalOutputFormat.PLAIN_TEXT);

        assertThat(result).isEqualTo("""
                제목

                01. 섹션

                첫 문단이다.

                둘째 문단이다.""");
    }

    @Test
    @DisplayName("본문이 비어 있어도 NPE 없이 제목만 남긴다 (section_drafts.content 는 nullable)")
    void keepsHeadingWhenContentIsMissing() {
        String result = formatter.format("제목",
                List.of(new SectionOutput(1, "빈 섹션", null),
                        new SectionOutput(2, "공백 섹션", "   "),
                        new SectionOutput(3, "정상 섹션", "본문")),
                FinalOutputFormat.PLAIN_TEXT);

        assertThat(result).isEqualTo("""
                제목

                01. 빈 섹션

                02. 공백 섹션

                03. 정상 섹션

                본문""");
    }

    @Test
    @DisplayName("문서 끝에 개행을 남기지 않는다")
    void doesNotLeaveTrailingNewline() {
        String result = formatter.format("제목", SECTIONS, FinalOutputFormat.MARKDOWN);

        assertThat(result).doesNotEndWith("\n");
    }

    @Test
    @DisplayName("줄바꿈은 \\n 만 사용한다 (\\r\\n 을 쓰면 붙여 넣을 때 빈 줄이 두 배가 된다)")
    void usesLineFeedOnly() {
        String result = formatter.format("제목", SECTIONS, FinalOutputFormat.PLAIN_TEXT);

        assertThat(result).doesNotContain("\r");
    }

    @Test
    @DisplayName("결과물 유형·섹션 수 같은 화면 메타 정보는 담지 않는다")
    void omitsScreenMetadata() {
        String result = formatter.format("장학금 매칭 서비스 제안서", SECTIONS, FinalOutputFormat.PLAIN_TEXT);

        assertThat(result).doesNotContain("제안서 ·").doesNotContain("개 섹션");
    }
}
