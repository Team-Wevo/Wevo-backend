package com.wevo.backend.project.repository;

import com.wevo.backend.project.domain.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

    /**
     * 프로젝트의 활성 초대 링크를 조회한다. (재사용 링크 정책상 프로젝트당 1개)
     *
     * <p>동시 발급 경합으로 활성 링크가 중복 저장되더라도 첫 번째만 반환해
     * {@code NonUniqueResultException} 으로 초대 기능이 마비되지 않도록 {@code findFirst} 를 쓴다.
     */
    Optional<InviteLink> findFirstByProjectIdAndIsActiveTrue(Long projectId);

    /**
     * 토큰 해시로 활성 초대 링크를 프로젝트와 함께 조회한다.
     *
     * <p>원문이 아니라 해시로 찾는다 — DB 에는 해시만 저장하므로 호출측이 요청 토큰을 같은 방식으로
     * 해시해 넘긴다. 원문 토큰이 이 계층까지 내려오지 않게 하려는 의도도 있다.
     *
     * <p>미리보기·참여에서 곧바로 프로젝트 정보가 필요하므로 {@code JOIN FETCH} 로 미리 로딩한다.
     */
    @Query("SELECT i FROM InviteLink i JOIN FETCH i.project "
            + "WHERE i.tokenHash = :tokenHash AND i.isActive = true")
    Optional<InviteLink> findActiveWithProjectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 프로젝트의 활성 초대 링크를 모두 조회한다. (보관 처리 시 일괄 비활성화 — §3.2.9)
     *
     * <p>정책상 활성 링크는 프로젝트당 1개지만, 동시 발급 경합으로 중복 저장된 행이 남아 있을 수
     * 있어 목록으로 받아 전부 끈다 — 하나만 끄면 남은 링크로 합류가 계속 가능하다.
     */
    List<InviteLink> findAllByProjectIdAndIsActiveTrue(Long projectId);
}
