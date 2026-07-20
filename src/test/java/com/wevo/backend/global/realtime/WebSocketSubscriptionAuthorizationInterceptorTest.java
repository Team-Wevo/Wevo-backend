package com.wevo.backend.global.realtime;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionAuthorizationInterceptorTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long SECTION_ID = 20L;
    private static final Long USER_ID = 7L;

    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private SectionAccessGuard sectionAccessGuard;
    @Mock
    private MessageChannel channel;

    private WebSocketSubscriptionAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketSubscriptionAuthorizationInterceptor(
                projectAccessGuard,
                sectionAccessGuard
        );
    }

    @Test
    @DisplayName("프로젝트 멤버는 프로젝트 topic을 구독할 수 있다")
    void subscribe_projectTopic_checksMembership() {
        Message<byte[]> message = subscribeMessage("/topic/projects/10", true);

        assertDoesNotThrow(() -> interceptor.preSend(message, channel));

        verify(projectAccessGuard).requireParticipant(PROJECT_ID, USER_ID);
        verifyNoInteractions(sectionAccessGuard);
    }

    @Test
    @DisplayName("프로젝트와 섹션이 일치하는 멤버는 섹션 topic을 구독할 수 있다")
    void subscribe_sectionTopic_checksSectionMembershipAndProject() {
        ProjectSection section = section(PROJECT_ID);
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willReturn(section);
        Message<byte[]> message = subscribeMessage(
                "/topic/projects/10/sections/20",
                true
        );

        assertDoesNotThrow(() -> interceptor.preSend(message, channel));

        verify(sectionAccessGuard).requireParticipantSection(SECTION_ID, USER_ID);
        verifyNoInteractions(projectAccessGuard);
    }

    @Test
    @DisplayName("destination의 프로젝트와 실제 섹션 소속이 다르면 S001로 숨긴다")
    void subscribe_sectionTopicWithMismatchedProject_rejectsNotFound() {
        given(sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID))
                .willReturn(section(99L));

        assertRejected(
                subscribeMessage("/topic/projects/10/sections/20", true),
                ErrorCode.SECTION_NOT_FOUND
        );
    }

    @Test
    @DisplayName("비멤버의 프로젝트 topic 구독은 P001로 존재를 숨긴다")
    void subscribe_projectTopicAsNonMember_hidesProject() {
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        assertRejected(subscribeMessage("/topic/projects/10", true),
                ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("인증된 사용자는 자신의 오류 queue를 구독할 수 있다")
    void subscribe_userErrorQueue_allowsAuthenticatedUser() {
        assertDoesNotThrow(() -> interceptor.preSend(
                subscribeMessage("/user/queue/errors", true),
                channel
        ));

        verifyNoInteractions(projectAccessGuard, sectionAccessGuard);
    }

    @Test
    @DisplayName("인증 없이 구독하면 A001로 거부한다")
    void subscribe_withoutAuthentication_rejectsUnauthorized() {
        assertRejected(subscribeMessage("/topic/projects/10", false),
                ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("허용 목록 밖 destination 구독은 A002로 거부한다")
    void subscribe_unknownDestination_rejectsForbidden() {
        assertRejected(subscribeMessage("/queue/raw", true), ErrorCode.FORBIDDEN);
        verifyNoInteractions(projectAccessGuard, sectionAccessGuard);
    }

    private void assertRejected(Message<byte[]> message, ErrorCode expected) {
        WebSocketSecurityException exception = assertThrows(
                WebSocketSecurityException.class,
                () -> interceptor.preSend(message, channel)
        );
        assertThat(exception.getErrorCode()).isEqualTo(expected);
    }

    private Message<byte[]> subscribeMessage(String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    new AuthPrincipal(USER_ID),
                    null,
                    AuthorityUtils.NO_AUTHORITIES
            ));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ProjectSection section(Long projectId) {
        Project project = Project.builder().title("프로젝트").build();
        ReflectionTestUtils.setField(project, "id", projectId);
        return ProjectSection.builder().project(project).title("문제 정의").build();
    }
}
