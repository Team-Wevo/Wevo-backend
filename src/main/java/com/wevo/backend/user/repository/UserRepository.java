package com.wevo.backend.user.repository;

import com.wevo.backend.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * 사용자 행을 <b>배타 잠금</b>으로 조회한다.
     *
     * <p>"이 사용자가 활성 프로젝트의 OWNER 인가"를 검사하는 쪽(회원 탈퇴)과 그 사실을 만드는 쪽
     * (프로젝트 생성)을 직렬화하기 위한 것이다. 잠금이 없으면 다음 순서가 성립한다.
     *
     * <pre>
     * 탈퇴: 소유 프로젝트 없음 확인 ─────────────────────┐
     * 생성:              프로젝트 + OWNER 멤버십 저장·커밋 │
     * 탈퇴:                                    WITHDRAWN 커밋 ┘
     * </pre>
     *
     * <p>결과는 <b>탈퇴한 사용자가 활성 프로젝트의 OWNER 로 남는 상태</b>다. MVP 에 팀장 위임이
     * 없어(정책서 §1.2) 그 프로젝트는 삭제할 사람이 영영 없어진다 — {@code U003} 검사가 막으려던
     * 바로 그 상태다. 두 경로가 이 메서드로 같은 행을 먼저 잡으면 한쪽이 반드시 기다린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
