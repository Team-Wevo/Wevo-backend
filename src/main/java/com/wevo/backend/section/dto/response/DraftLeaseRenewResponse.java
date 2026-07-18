package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.DraftLease;
import java.time.LocalDateTime;

/**
 * 초안 편집 잠금 갱신 결과.
 */
public record DraftLeaseRenewResponse(
        LocalDateTime expiresAt
) {

    public static DraftLeaseRenewResponse from(DraftLease lease) {
        return new DraftLeaseRenewResponse(lease.getLeaseUntil());
    }
}
