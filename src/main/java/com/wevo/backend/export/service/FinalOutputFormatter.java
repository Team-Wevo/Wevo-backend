package com.wevo.backend.export.service;

import com.wevo.backend.export.dto.response.FinalOutputResponse.SectionOutput;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 확정본 섹션 목록을 클립보드·파일에 그대로 쓸 수 있는 <b>문자열 하나</b>로 조립한다.
 *
 * <p>기존의 조회 API({@code final-output})는 섹션을 배열로 반환하므로 클립보드에 넣을 수 없다.
 * 포맷 형식을 정해, 복사본과 이후 추가될 다운로드 파일이 같은 결과를 내도록 한다.
 *
 * <p>서비스·컨트롤러에 조립을 넣지 않고 독립 컴포넌트로 두는 이유도 같다. 파일 다운로드가
 * 추가될 때 이 클래스를 그대로 재사용한다.
 *
 * <h2>포맷 계약</h2> - 피그마 기준
 * <ul>
 *   <li>프로젝트 제목을 맨 위에 1회. 마크다운은 {@code # }</li>
 *   <li>섹션 번호는 <b>두 자리 zero-padding + 마침표</b>({@code 01. 제안 배경}) — 완성본 화면 표기와
 *       같게 해서 본 대로 복사되게 한다</li>
 *   <li>섹션 제목은 마크다운에서 {@code ## }</li>
 *   <li>제목↔본문, 본문↔다음 제목 사이는 빈 줄 1개</li>
 *   <li>줄바꿈은 {@code \n} 고정 — {@code \r\n} 을 쓰면 붙여 넣는 환경에 따라 빈 줄이 두 배로 보인다</li>
 *   <li>본문은 앞뒤 공백만 제거하고 <b>내부 줄바꿈·문단 구조는 보존</b>한다</li>
 *   <li>문서 끝에 개행을 남기지 않는다</li>
 * </ul>
 *
 * <p>결과물 유형·섹션 수({@code 제안서 · 6개 섹션})는 <b>담지 않는다</b> — 완성본 화면의 안내용
 * 메타 정보이지 문서 본문이 아니라, 붙여 넣은 제안서에 남으면 지우게 되는 줄이다.
 */
@Component
public class FinalOutputFormatter {

    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String SECTION_NUMBER_FORMAT = "%02d. %s";
    private static final String MARKDOWN_TITLE_PREFIX = "# ";
    private static final String MARKDOWN_HEADING_PREFIX = "## ";

    /**
     * 완성본을 지정한 형식의 문자열로 조립한다.
     *
     * @param projectTitle 프로젝트 제목 — 문서 맨 위에 온다
     * @param sections     확정본 섹션 목록 (섹션 순서대로 정렬된 상태로 전달받는다)
     */
    public String format(String projectTitle, List<SectionOutput> sections, FinalOutputFormat format) {
        boolean markdown = format == FinalOutputFormat.MARKDOWN;

        List<String> blocks = new ArrayList<>();
        blocks.add(markdown ? MARKDOWN_TITLE_PREFIX + projectTitle : projectTitle);

        // 번호는 저장된 sectionOrder 를 그대로 쓴다. 목록 인덱스로 매기면 조회 순서가 바뀔 때
        // 화면 번호와 복사본 번호가 어긋난다.
        for (SectionOutput section : sections) {
            String heading = SECTION_NUMBER_FORMAT.formatted(section.order(), section.title());
            blocks.add(markdown ? MARKDOWN_HEADING_PREFIX + heading : heading);

            // 저장 경로는 본문을 @NotBlank 로 막지만 컬럼(section_drafts.content)은 nullable 이다.
            // 여기서 NPE 로 500 을 내면 조회 API 는 되는데 복사만 실패하므로, 제목만 남기고 넘어간다.
            String content = section.content();
            if (content != null && !content.isBlank()) {
                blocks.add(content.strip());
            }
        }

        return String.join(BLOCK_SEPARATOR, blocks);
    }
}
