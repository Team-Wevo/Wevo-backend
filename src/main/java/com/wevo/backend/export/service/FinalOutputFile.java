package com.wevo.backend.export.service;

/**
 * 다운로드로 내보낼 완성본 파일 한 건.
 *
 * <p>JSON 으로 직렬화되지 않는 <b>서비스 → 컨트롤러 전달용</b> 값이라 {@code dto/response} 가 아니라
 * 서비스 패키지에 둔다.
 *
 * @param fileName      사용자에게 보일 파일명 (한글 포함 가능 — {@code Content-Disposition} 의 {@code filename*})
 * @param asciiFileName {@code filename*} 을 해석하지 못하는 클라이언트를 위한 ASCII 전용 대체 파일명
 * @param content       조립된 완성본 본문
 */
public record FinalOutputFile(String fileName, String asciiFileName, String content) {
}
