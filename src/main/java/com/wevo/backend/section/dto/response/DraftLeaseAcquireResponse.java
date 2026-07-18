package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.DraftLease;
import java.time.LocalDateTime;

/**
 * 초안 편집 잠금 획득 결과.
 */
public record DraftLeaseAcquireResponse(
        Long leaseId,
        Long projectSectionId,
        HolderResponse holder,
        LocalDateTime leaseUntil
) {

    public static DraftLeaseAcquireResponse from(DraftLease lease) {
        return new DraftLeaseAcquireResponse(
                lease.getId(),
                lease.getProjectSection().getId(),
                new HolderResponse(lease.getHolder().getId(), lease.getHolder().getName()),
                lease.getLeaseUntil()
        );
    }

    public record HolderResponse(Long id, String name) {
    }
}
