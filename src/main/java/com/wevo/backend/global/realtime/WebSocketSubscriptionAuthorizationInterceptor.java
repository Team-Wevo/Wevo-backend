package com.wevo.backend.global.realtime;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트·섹션 알림 구독에 HTTP API와 같은 객체 단위 멤버십 검사를 적용한다.
 *
 * <p>인증 여부만으로 임의 프로젝트·섹션의 topic을 구독하지 못하도록 기존 접근 가드를 재사용하고,
 * 비멤버에게는 HTTP API와 동일하게 대상의 존재를 숨긴다.
 */
@Component
public class WebSocketSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern PROJECT_TOPIC =
            Pattern.compile("^/topic/projects/(\\d+)$");
    private static final Pattern SECTION_TOPIC =
            Pattern.compile("^/topic/projects/(\\d+)/sections/(\\d+)$");
    private static final String USER_ERROR_QUEUE = "/user/queue/errors";

    private final ProjectAccessGuard projectAccessGuard;
    private final SectionAccessGuard sectionAccessGuard;

    public WebSocketSubscriptionAuthorizationInterceptor(
            ProjectAccessGuard projectAccessGuard,
            SectionAccessGuard sectionAccessGuard
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.sectionAccessGuard = sectionAccessGuard;
    }

    /**
     * SUBSCRIBE 프레임에만 destination별 접근 검사를 적용한다.
     *
     * <p>개인 오류 queue는 인증 사용자에게 허용하고, 프로젝트·섹션 topic은 멤버십 확인 후 허용한다.
     * 명시되지 않은 topic 또는 raw queue 구독은 기본 거부한다.
     */
    @Override
    @Transactional(readOnly = true)
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        Long userId = requireUserId(accessor.getUser());
        String destination = accessor.getDestination();
        if (USER_ERROR_QUEUE.equals(destination)) {
            return message;
        }

        try {
            Matcher sectionMatcher = SECTION_TOPIC.matcher(nullToEmpty(destination));
            if (sectionMatcher.matches()) {
                authorizeSection(sectionMatcher, userId);
                return message;
            }

            Matcher projectMatcher = PROJECT_TOPIC.matcher(nullToEmpty(destination));
            if (projectMatcher.matches()) {
                authorizeProject(projectMatcher, userId);
                return message;
            }
        } catch (NumberFormatException exception) {
            throw new WebSocketSecurityException(ErrorCode.FORBIDDEN);
        } catch (BusinessException exception) {
            throw new WebSocketSecurityException(hideProjectMembership(exception.getErrorCode()));
        }

        throw new WebSocketSecurityException(ErrorCode.FORBIDDEN);
    }

    /** 프로젝트 topic 구독자가 해당 프로젝트의 OWNER 또는 MEMBER인지 확인한다. */
    private void authorizeProject(Matcher matcher, Long userId) {
        Long projectId = Long.valueOf(matcher.group(1));
        projectAccessGuard.requireParticipant(projectId, userId);
    }

    /**
     * 섹션 멤버십과 destination에 적힌 projectId가 실제 섹션 소속과 일치하는지 함께 확인한다.
     */
    private void authorizeSection(Matcher matcher, Long userId) {
        Long projectId = Long.valueOf(matcher.group(1));
        Long sectionId = Long.valueOf(matcher.group(2));
        ProjectSection section = sectionAccessGuard.requireParticipantSection(sectionId, userId);
        if (!section.getProject().getId().equals(projectId)) {
            throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        }
    }

    /** CONNECT 단계에서 설정된 AuthPrincipal만 구독 행위자로 신뢰한다. */
    private Long requireUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthPrincipal authPrincipal) {
            return authPrincipal.userId();
        }
        throw new WebSocketSecurityException(ErrorCode.UNAUTHORIZED);
    }

    /** 프로젝트 비멤버 오류를 존재 숨김 계약의 PROJECT_NOT_FOUND로 변환한다. */
    private ErrorCode hideProjectMembership(ErrorCode errorCode) {
        return errorCode == ErrorCode.NOT_PROJECT_MEMBER
                ? ErrorCode.PROJECT_NOT_FOUND
                : errorCode;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
