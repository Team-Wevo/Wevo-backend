package com.wevo.backend.global.validation;

/**
 * 사용자 입력 길이 정책에 사용하는 공통 계산 함수.
 */
public final class TextLengthPolicy {

    private TextLengthPolicy() {
    }

    /**
     * 기존 {@link String#length()}의 UTF-16 단위는 유지하면서 공백 문자만 길이에서 제외한다.
     */
    public static boolean exceedsNonWhitespaceLimit(CharSequence value, int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max는 0 이상이어야 합니다.");
        }
        if (value == null) {
            return false;
        }

        int nonWhitespaceLength = 0;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))
                    && ++nonWhitespaceLength > max) {
                return true;
            }
        }
        return false;
    }
}
