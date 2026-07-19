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

    /**
     * 다른 도메인이 사용자 연관관계(FK)를 걸 때 쓰는 <b>지연 참조</b>를 돌려준다.
     *
     * <p>타 도메인이 {@code UserRepository}를 직접 참조하지 않게 하기 위한 공개 진입점이다.
     * (CLAUDE.md §6 — 도메인 간 접근) 실제 SELECT 없이 프록시만 만들므로, 저장할 엔티티의
     * 연관 필드를 채우는 용도로만 사용하고 필드 값을 읽는 용도로는 쓰지 않는다.
     */
    public User getUserReference(Long userId) {
        return userRepository.getReferenceById(userId);
    }

    /**
     * 다른 도메인이 사용자 엔티티를 직접 참조하지 않고 표시 이름을 조회하는 공개 진입점이다.
     */
    public String getUserName(Long userId) {
        return findUser(userId).getName();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
