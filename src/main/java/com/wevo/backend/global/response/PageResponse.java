package com.wevo.backend.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 조회의 공통 페이지 응답. (CLAUDE.md §5.5 · API_SPEC §1.5)
 *
 * <p>페이지 메타데이터의 형태를 API 마다 다르게 만들지 않기 위한 공통 타입이다 — 계약이 한 번
 * 갈리면 화면마다 다른 필드를 읽게 되고, 되돌리려면 전부 계약 변경이 된다.
 *
 * <p><b>요청 크기 상한은 거부가 아니라 조정</b>이다 — {@code size} 가 {@link #MAX_SIZE} 를 넘으면
 * 오류로 막지 않고 상한으로 낮춰 조회한다 (CLAUDE.md §5.5 "초과 요청은 서버가 100으로 제한").
 * 응답의 {@code size} 에는 <b>실제 적용된 크기</b>가 담기므로 클라이언트가 조정 사실을 알 수 있다.
 *
 * @param content       현재 페이지의 항목들
 * @param page          0부터 시작하는 페이지 번호
 * @param size          실제 적용된 페이지 크기 (상한으로 조정됐을 수 있다)
 * @param totalElements 조건에 맞는 전체 항목 수 — 현재 페이지 길이와 구분된다
 * @param totalPages    전체 페이지 수
 * @param hasNext       다음 페이지 존재 여부
 */
@Schema(requiredProperties = {
        "content", "page", "size", "totalElements", "totalPages", "hasNext"
})
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /** 페이지 크기 상한. 초과 요청은 거부하지 않고 이 값으로 낮춘다. (CLAUDE.md §5.5) */
    public static final int MAX_SIZE = 100;

    /** 페이지 크기 기본값. (CLAUDE.md §5.5) */
    public static final int DEFAULT_SIZE = 20;

    /**
     * 조회 결과 페이지를 응답 DTO 페이지로 변환한다.
     *
     * <p>항목 변환 함수를 받아 <b>엔티티·조회 모델이 그대로 실려 나가지 않게</b> 한다.
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    /**
     * 요청한 페이지 크기를 유효 범위로 조정한다.
     *
     * <p>상한 초과는 {@link #MAX_SIZE}, 1 미만은 {@link #DEFAULT_SIZE} 로 본다 — 목록 조회는
     * 잘못된 크기 하나로 실패시킬 만큼 위험한 요청이 아니고, 공통 규칙도 "서버가 제한"으로 정해져
     * 있다. 크기를 오류로 막으면 화면이 빈 목록 대신 오류 화면을 띄우게 된다.
     */
    public static int normalizeSize(int requestedSize) {
        if (requestedSize < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(requestedSize, MAX_SIZE);
    }

    /** 요청한 페이지 번호를 유효 범위로 조정한다. 음수는 첫 페이지로 본다. */
    public static int normalizePage(int requestedPage) {
        return Math.max(requestedPage, 0);
    }
}
