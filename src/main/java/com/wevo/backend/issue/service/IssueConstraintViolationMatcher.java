package com.wevo.backend.issue.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** issue 쓰기 경계에서 DB 제약 이름을 안전하게 판별한다. */
final class IssueConstraintViolationMatcher {

    private IssueConstraintViolationMatcher() {
    }

    static boolean matches(
            DataIntegrityViolationException exception,
            String expectedConstraint
    ) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return expectedConstraint.equals(constraintViolation.getConstraintName());
            }
            current = current.getCause();
        }
        return false;
    }
}
