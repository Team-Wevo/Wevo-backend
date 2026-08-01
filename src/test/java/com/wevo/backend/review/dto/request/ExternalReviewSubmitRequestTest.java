package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.UnderstandingSignal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이해도별 summary 필수 규칙 검증. (정책서 §6.2.3)
 *
 * <p>CLEAR/PARTIAL 은 이해한 핵심 문장이 있어야 작성자 의도와 대조할 수 있다.
 * UNCLEAR 는 "이해하지 못했다"가 답 자체라 문장을 강제하지 않는다.
 */
class ExternalReviewSubmitRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @EnumSource(value = UnderstandingSignal.class, names = {"CLEAR", "PARTIAL"})
    @DisplayName("이해됨·애매함은 summary 가 없으면 검증에 실패한다")
    void rejectsMissingSummaryForUnderstoodSignals(UnderstandingSignal signal) {
        ExternalReviewSubmitRequest request =
                new ExternalReviewSubmitRequest(signal, "외부검토자", null, null);

        assertThat(violatedProperties(request)).contains("summaryPresentForUnderstoodSignal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "　"})
    @DisplayName("공백만 담긴 summary 는 없는 것으로 본다")
    void rejectsBlankSummaryForUnderstoodSignals(String blank) {
        ExternalReviewSubmitRequest request =
                new ExternalReviewSubmitRequest(UnderstandingSignal.CLEAR, "외부검토자", blank, null);

        assertThat(violatedProperties(request)).contains("summaryPresentForUnderstoodSignal");
    }

    @ParameterizedTest
    @EnumSource(value = UnderstandingSignal.class, names = {"CLEAR", "PARTIAL"})
    @DisplayName("이해됨·애매함도 summary 가 있으면 통과한다")
    void acceptsSummaryForUnderstoodSignals(UnderstandingSignal signal) {
        ExternalReviewSubmitRequest request =
                new ExternalReviewSubmitRequest(signal, "외부검토자", "핵심을 이해했어요.", null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("이해 어려움은 summary 가 없어도 통과한다")
    void acceptsMissingSummaryForUnclearSignal() {
        ExternalReviewSubmitRequest request =
                new ExternalReviewSubmitRequest(UnderstandingSignal.UNCLEAR, "외부검토자", null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(UnderstandingSignal.class)
    @DisplayName("추가 코멘트는 이해도와 무관하게 없어도 통과한다 (정책서 §6.2.3 — 추가 코멘트는 선택)")
    void commentIsOptionalForEverySignal(UnderstandingSignal signal) {
        ExternalReviewSubmitRequest request =
                new ExternalReviewSubmitRequest(signal, "외부검토자", "핵심을 이해했어요.", null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.reviewerComment()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "　"})
    @DisplayName("공백만 담긴 추가 코멘트는 null 로 정규화한다")
    void normalizesBlankCommentToNull(String blank) {
        ExternalReviewSubmitRequest request = new ExternalReviewSubmitRequest(
                UnderstandingSignal.CLEAR, "외부검토자", "핵심을 이해했어요.", blank);

        assertThat(request.reviewerComment()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("입력한 추가 코멘트는 그대로 보존한다")
    void keepsProvidedComment() {
        ExternalReviewSubmitRequest request = new ExternalReviewSubmitRequest(
                UnderstandingSignal.PARTIAL, "외부검토자", "핵심을 이해했어요.", "  2문단이 길어요.  ");

        assertThat(request.reviewerComment()).isEqualTo("  2문단이 길어요.  ");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("이해도가 없으면 summary 규칙이 아니라 understandingSignal 누락으로 실패한다")
    void reportsMissingSignalOnly() {
        ExternalReviewSubmitRequest request =
                new ExternalReviewSubmitRequest(null, "외부검토자", null, null);

        assertThat(violatedProperties(request))
                .containsExactly("understandingSignal");
    }

    private Set<String> violatedProperties(ExternalReviewSubmitRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
