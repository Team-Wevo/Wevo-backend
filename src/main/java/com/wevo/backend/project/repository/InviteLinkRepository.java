package com.wevo.backend.project.repository;

import com.wevo.backend.project.domain.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

    /**
     * 프로젝트의 활성 초대 링크를 조회한다. (재사용 링크 정책상 프로젝트당 최대 1개)
     */
    Optional<InviteLink> findByProjectIdAndIsActiveTrue(Long projectId);

    /**
     * 토큰으로 활성 초대 링크를 프로젝트와 함께 조회한다.
     *
     * <p>미리보기·참여에서 곧바로 프로젝트 정보가 필요하므로 {@code JOIN FETCH} 로 미리 로딩한다.
     */
    @Query("SELECT i FROM InviteLink i JOIN FETCH i.project "
            + "WHERE i.token = :token AND i.isActive = true")
    Optional<InviteLink> findActiveWithProjectByToken(@Param("token") String token);
}
