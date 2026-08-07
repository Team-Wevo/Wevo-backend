package com.wevo.backend.opinion.dto.request;

import com.wevo.backend.opinion.domain.Opinion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 의견 임시저장 요청. (제품 정책서 §4.1)
 *
 * <p>임시저장은 <b>1~1,000자</b>를 허용한다 — 작성 중 자동 저장이 짧은 본문에서도 가능해야 하므로,
 * 하한 20자({@link Opinion#MIN_CONTENT_LENGTH})는 <b>제출 시점</b>에 검증한다. (API_SPEC §3.4.2)
 */
@Schema(example = """
        {
          "content": "우리 팀이 겪는 문제는 회의 뒤 결정이 어디에도 남지 않는다는 점입니다."
        }""")
public record OpinionDraftRequest(
        @NotBlank
        @Size(max = Opinion.MAX_CONTENT_LENGTH)
        String content
) {
}
