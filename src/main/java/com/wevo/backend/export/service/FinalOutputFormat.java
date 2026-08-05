package com.wevo.backend.export.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 완성본을 문자열로 조립할 때의 출력 형식.
 *
 * <p>클립보드 복사와 파일 다운로드가 <b>같은 형식 목록</b>을 공유하도록
 * enum 으로 둔다. 두 경로의 차이는 응답을 JSON 으로 감싸는지 파일로 내보내는지일 뿐이며, 조립
 * 결과는 같아야 한다.
 *
 * <p>{@code fileExtension} 은 <b>파일 형식</b>의 속성이지 HTTP 의 속성이 아니므로 여기에 둔다.
 * Content-Type 등 전송 계층의 표현은 컨트롤러가 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum FinalOutputFormat {

    /** 일반 텍스트 */
    PLAIN_TEXT("txt"),

    /** 마크다운 */
    MARKDOWN("md");

    /** 다운로드 파일명에 붙는 확장자. (마침표 제외) */
    private final String fileExtension;
}
