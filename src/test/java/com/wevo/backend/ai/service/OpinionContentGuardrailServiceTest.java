package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpinionContentGuardrailServiceTest {

    private static final Long SECTION_ID = 5L;
    private static final Long USER_ID = 7L;
    private static final String CONTENT = "회의 결과가 어디에도 남지 않아 매번 반복됩니다.";

    @Mock
    private ObjectProvider<OpinionGuardrailClassifier> classifierProvider;
    @Mock
    private OpinionGuardrailClassifier classifier;
    @Mock
    private UserService userService;

    private OpinionContentGuardrailService guardrailService;

    @BeforeEach
    void setUp() {
        guardrailService = new OpinionContentGuardrailService(classifierProvider, userService);
    }

    @Test
    @DisplayName("AI 미구성(classifier 부재)이면 가드레일을 건너뛰고 제출을 허용한다")
    void classifierAbsent_allows() {
        given(classifierProvider.getIfAvailable()).willReturn(null);

        assertThatCode(() -> guardrailService.requireAcceptable(section(), USER_ID, CONTENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("통과 판정이면 예외 없이 제출을 허용한다")
    void acceptableVerdict_allows() {
        given(classifierProvider.getIfAvailable()).willReturn(classifier);
        given(userService.getUserReference(USER_ID)).willReturn(userRef());
        given(classifier.classify(any(), any(), any(), any()))
                .willReturn(new OpinionGuardrailVerdict(true, "OK"));

        assertThatCode(() -> guardrailService.requireAcceptable(section(), USER_ID, CONTENT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("거부 판정이면 OPINION_CONTENT_REJECTED 로 제출을 막는다")
    void unacceptableVerdict_rejects() {
        given(classifierProvider.getIfAvailable()).willReturn(classifier);
        given(userService.getUserReference(USER_ID)).willReturn(userRef());
        given(classifier.classify(any(), any(), any(), any()))
                .willReturn(new OpinionGuardrailVerdict(false, "GIBBERISH"));

        BusinessException ex = (BusinessException) org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> guardrailService.requireAcceptable(section(), USER_ID, CONTENT));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPINION_CONTENT_REJECTED);
    }

    @Test
    @DisplayName("AI 는 구성됐으나 판정에 실패하면 fail-closed — OPINION_GUARDRAIL_UNAVAILABLE 로 막는다")
    void classifierThrows_failClosed() {
        given(classifierProvider.getIfAvailable()).willReturn(classifier);
        given(userService.getUserReference(USER_ID)).willReturn(userRef());
        given(classifier.classify(any(), any(), any(), any()))
                .willThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> guardrailService.requireAcceptable(section(), USER_ID, CONTENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OPINION_GUARDRAIL_UNAVAILABLE);
    }

    private ProjectSection section() {
        Project project = Project.builder()
                .title("p").resultType(OutputType.PRESENTATION).status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", 1L);
        ProjectSection section = ProjectSection.builder()
                .project(project).title("문제 정의").sectionOrder(1)
                .status(ProjectSectionStatus.COLLECTING).build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        return section;
    }

    private User userRef() {
        User user = User.builder().name("호석").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
