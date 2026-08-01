package com.wevo.backend.export.service;

/**
 * 완성본을 문자열로 조립할 때의 출력 형식.
 *
 * <p>클립보드 복사(§3.6.2·§3.6.3)와 파일 다운로드가 <b>같은 형식 목록</b>을 공유하도록 enum 으로
 * 둔다. 두 경로의 차이는 응답을 JSON 으로 감싸는지 파일로 내보내는지일 뿐이며, 조립 결과는 같아야
 * 한다.
 */
public enum FinalOutputFormat {

    /** 일반 텍스트 */
    PLAIN_TEXT,

    /** 마크다운 */
    MARKDOWN
}
