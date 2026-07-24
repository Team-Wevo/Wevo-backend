package com.wevo.backend.ai.context;

/** 의견 중복·누락이 있는 chunk plan을 성공 계획으로 반환하지 않는다. */
public class IncompleteContextCoverageException extends IllegalStateException {

    public IncompleteContextCoverageException() {
        super("AI context chunk의 eligible opinion coverage가 완전하지 않습니다.");
    }
}
