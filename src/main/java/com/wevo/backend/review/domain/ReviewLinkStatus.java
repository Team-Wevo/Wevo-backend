package com.wevo.backend.review.domain;

public enum ReviewLinkStatus {
    ACTIVE, //활성화
    OUTDATED, //본문 수정으로 만료
    EXPIRED, //유효 기간 만료
    CLOSED //비활성화
}
