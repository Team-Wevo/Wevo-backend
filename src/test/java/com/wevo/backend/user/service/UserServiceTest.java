package com.wevo.backend.user.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.domain.UserWithdrawnEvent;
import com.wevo.backend.user.dto.request.ProfileUpdateRequest;
import com.wevo.backend.user.dto.response.MyProfileResponse;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectOwnershipQuery projectOwnershipQuery;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("내 프로필을 조회하면 이름·이메일·프로필사진·상태를 반환한다")
    void getMyProfile_returnsProfile() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("호석", "user@wevo.com")));

        MyProfileResponse response = userService.getMyProfile(USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.name()).isEqualTo("호석");
        assertThat(response.email()).isEqualTo("user@wevo.com");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("이메일이 없는 사용자도 프로필 조회 시 email이 null로 반환된다")
    void getMyProfile_withNullEmail() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("카카오유저", null)));

        MyProfileResponse response = userService.getMyProfile(USER_ID);

        assertThat(response.email()).isNull();
        assertThat(response.name()).isEqualTo("카카오유저");
    }

    @Test
    @DisplayName("표시 이름을 수정하면 변경된 이름을 반환한다")
    void updateMyProfile_updatesName() {
        User user = user("이전이름", "user@wevo.com");
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        MyProfileResponse response = userService.updateMyProfile(USER_ID, new ProfileUpdateRequest("새이름"));

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(user.getName()).isEqualTo("새이름");
        // 이메일은 그대로 유지
        assertThat(user.getEmail()).isEqualTo("user@wevo.com");
    }

    @Test
    @DisplayName("탈퇴한 계정은 프로필을 수정할 수 없다 — 마스킹된 이름 되돌리기 차단")
    void updateMyProfile_withdrawnUser_throws() {
        // 탈퇴 직후 30분간 살아 있는 Access Token 으로 "탈퇴한 사용자" 를 실명으로 되돌리는 경로.
        User user = user("이전이름", "user@wevo.com");
        user.withdraw();
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.updateMyProfile(USER_ID, new ProfileUpdateRequest("복원한이름")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(user.getName()).isEqualTo(User.WITHDRAWN_NAME);
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외를 던진다")
    void getMyProfile_userNotFound_throws() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> userService.getMyProfile(USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴하면 상태가 WITHDRAWN 이 되고 개인 식별정보가 지워진다")
    void withdraw_scrubsPersonalData() {
        User user = user("호석", "user@wevo.com");
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(projectOwnershipQuery.hasActiveOwnedProject(USER_ID)).willReturn(false);

        userService.withdraw(USER_ID);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getName()).isEqualTo(User.WITHDRAWN_NAME);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        // 소셜 연결 해제·Refresh Token 폐기는 auth 도메인이 이 이벤트를 받아 처리한다.
        then(eventPublisher).should().publishEvent(new UserWithdrawnEvent(USER_ID));
    }

    @Test
    @DisplayName("보관되지 않은 프로젝트의 OWNER 면 탈퇴를 거부한다")
    void withdraw_ownsActiveProject_throws() {
        User user = user("호석", "user@wevo.com");
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(projectOwnershipQuery.hasActiveOwnedProject(USER_ID)).willReturn(true);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> userService.withdraw(USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_OWNS_ACTIVE_PROJECT);
        // 거부됐으므로 계정은 그대로여야 한다.
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmail()).isEqualTo("user@wevo.com");
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 탈퇴한 계정으로 다시 호출해도 멱등하게 성공한다")
    void withdraw_alreadyWithdrawn_isIdempotent() {
        // 탈퇴 직후에도 남은 Access Token 이 30분간 유효해 재시도가 실제로 들어올 수 있다.
        User user = user("호석", "user@wevo.com");
        user.withdraw();
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        userService.withdraw(USER_ID);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        // 정리는 한 번만 일어나야 한다.
        then(eventPublisher).shouldHaveNoInteractions();
        then(projectOwnershipQuery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 탈퇴하면 USER_NOT_FOUND 예외를 던진다")
    void withdraw_userNotFound_throws() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> userService.withdraw(USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user(String name, String email) {
        User user = User.builder()
                .name(name).email(email).profileImageUrl("http://img/p.png")
                .status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
