package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.DraftLease;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DraftLeaseRepository extends JpaRepository<DraftLease, Long> {

    Optional<DraftLease> findByProjectSection_Id(Long projectSectionId);

    /**
     * 섹션의 lease를 배타 잠금으로 조회한다. acquire와 renew가 같은 행을 동시에 변경하지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lease FROM DraftLease lease "
            + "JOIN FETCH lease.projectSection "
            + "WHERE lease.projectSection.id = :projectSectionId")
    Optional<DraftLease> findByProjectSectionIdForUpdate(
            @Param("projectSectionId") Long projectSectionId
    );

    /**
     * 특정 사용자가 보유한 <b>아직 만료되지 않은</b> lease를 배타 잠금으로 모두 조회한다. (탈퇴 정리용)
     *
     * <p>한 사용자가 여러 섹션의 편집권을 동시에 쥔 채 탈퇴할 수 있어 목록으로 받는다. 만료된 행은
     * 이미 다른 사람이 획득할 수 있는 상태라 건드릴 필요가 없다. acquire·renew 와 같은 행 잠금을
     * 잡아, 정리와 재획득이 뒤섞이지 않게 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lease FROM DraftLease lease "
            + "WHERE lease.holderUserId = :holderUserId AND lease.leaseUntil > :now")
    List<DraftLease> findActiveByHolderForUpdate(
            @Param("holderUserId") Long holderUserId,
            @Param("now") LocalDateTime now
    );

}
