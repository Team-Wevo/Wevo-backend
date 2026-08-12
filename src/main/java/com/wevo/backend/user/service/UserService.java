package com.wevo.backend.user.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserWithdrawnEvent;
import com.wevo.backend.user.dto.request.ProfileUpdateRequest;
import com.wevo.backend.user.dto.response.MyProfileResponse;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 조회·수정. (제품 정책서 §1.1)
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ProjectOwnershipQuery projectOwnershipQuery;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       ProjectOwnershipQuery projectOwnershipQuery,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.projectOwnershipQuery = projectOwnershipQuery;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 내 프로필을 조회한다.
     */
    public MyProfileResponse getMyProfile(Long userId) {
        return MyProfileResponse.from(findUser(userId));
    }

    /**
     * 내 표시 이름을 수정한다. (이메일·프로필 사진은 수정 대상이 아님)
     *
     * <p><b>탈퇴한 계정은 수정할 수 없다.</b> 탈퇴 직후에도 Access Token 이 30분간 살아 있는데,
     * 막지 않으면 탈퇴 시 {@code "탈퇴한 사용자"} 로 덮은 표시 이름을 되돌리거나 임의 값으로 바꿔
     * §3.3.3 의 개인정보 삭제를 무효화할 수 있다. 멤버 목록(§3.2.8)은 탈퇴자도 이름 그대로 노출하므로
     * 실명이 팀원 화면에 다시 보인다. 프로젝트 생성이 같은 창을 {@code U001} 로 막는 것과 같은 방어다.
     *
     * <p>사용자 행을 <b>배타 잠금</b>으로 잡고 시작한다. {@code User} 에 {@code @Version}·
     * {@code @DynamicUpdate} 가 없어, 잠그지 않으면 탈퇴 검사 통과 후 이름 변경이 flush 되는 사이
     * {@link #withdraw} 가 끼어들 때 그 마스킹 결과({@code "탈퇴한 사용자"}·{@code WITHDRAWN})를
     * 되돌린다. {@code withdraw} 도 같은 행을 배타 잠금으로 잡으므로 두 경로가 직렬화된다.
     */
    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.updateName(request.name());
        return MyProfileResponse.from(user);
    }

    /**
     * 회원 탈퇴를 처리한다. (API_SPEC §3.3.3)
     *
     * <p>팀장으로 있는 프로젝트가 남아 있으면 {@code U003} 으로 거부한다. MVP 에는 팀장 위임이
     * 없어서(정책서 §1.2) OWNER 가 빠지면 그 프로젝트를 삭제할 사람이 사라지기 때문이다.
     * 프로젝트를 대신 보관해 버리는 방식은 쓰지 않는다 — 남은 팀원 3명의 작업물이 예고 없이
     * 목록에서 사라진다. 사용자가 먼저 정리하도록 안내하는 편이 낫다는 <b>팀 결정</b>이다.
     *
     * <p>이미 탈퇴한 사용자의 재호출은 오류가 아니라 <b>멱등 성공</b>으로 둔다. 탈퇴 직후에도
     * 남은 Access Token 이 30분간 살아 있어 재시도가 실제로 들어올 수 있고, 그때 실패를 돌려주면
     * 화면이 "탈퇴에 실패했다"고 표시하는데 계정은 이미 탈퇴 상태다.
     *
     * <p>사용자 행을 <b>배타 잠금</b>으로 잡고 시작한다. 잠그지 않으면 "소유 프로젝트 없음" 확인과
     * {@code WITHDRAWN} 저장 사이에 프로젝트 생성이 끼어들어, 탈퇴한 사용자가 활성 프로젝트의
     * OWNER 로 남는다({@link com.wevo.backend.user.repository.UserRepository#findByIdForUpdate}).
     * 프로젝트 생성도 같은 행을 먼저 잡으므로 두 경로가 직렬화된다.
     *
     * @throws BusinessException 보관되지 않은 프로젝트의 OWNER 이면 {@link ErrorCode#USER_OWNS_ACTIVE_PROJECT}
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            return;
        }
        if (projectOwnershipQuery.hasActiveOwnedProject(userId)) {
            throw new BusinessException(ErrorCode.USER_OWNS_ACTIVE_PROJECT);
        }

        user.withdraw();
        // 소셜 연결 해제·Refresh Token 폐기는 auth 도메인이 받아서 처리한다. (같은 트랜잭션)
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
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
