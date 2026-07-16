package com.wevo.backend.user.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.dto.request.ProfileUpdateRequest;
import com.wevo.backend.user.dto.response.MyProfileResponse;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 조회·수정. (제품 정책서 §1.1)
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 내 프로필을 조회한다.
     */
    public MyProfileResponse getMyProfile(Long userId) {
        return MyProfileResponse.from(findUser(userId));
    }

    /**
     * 내 표시 이름을 수정한다. (이메일·프로필 사진은 수정 대상이 아님)
     */
    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, ProfileUpdateRequest request) {
        User user = findUser(userId);
        user.updateName(request.name());
        return MyProfileResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
