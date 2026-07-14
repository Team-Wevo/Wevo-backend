package com.wevo.backend.user.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.dto.request.ProfileUpdateRequest;
import com.wevo.backend.user.dto.response.MyProfileResponse;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

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
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        MyProfileResponse response = userService.updateMyProfile(USER_ID, new ProfileUpdateRequest("새이름"));

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(user.getName()).isEqualTo("새이름");
        // 이메일은 그대로 유지
        assertThat(user.getEmail()).isEqualTo("user@wevo.com");
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외를 던진다")
    void getMyProfile_userNotFound_throws() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> userService.getMyProfile(USER_ID));

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
