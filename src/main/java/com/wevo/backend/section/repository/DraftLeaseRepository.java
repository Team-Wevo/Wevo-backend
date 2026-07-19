package com.wevo.backend.section.repository;

import com.wevo.backend.section.domain.DraftLease;
import jakarta.persistence.LockModeType;
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

}
