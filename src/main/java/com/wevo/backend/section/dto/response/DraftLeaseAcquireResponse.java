package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.domain.DraftLease;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 초안 편집 잠금 획득 결과.
 */
@Schema(requiredProperties = {"expiresAt"})
public record DraftLeaseAcquireResponse(
        LocalDateTime expiresAt
) {

    public static DraftLeaseAcquireResponse from(DraftLease lease) {
        return new DraftLeaseAcquireResponse(lease.getLeaseUntil());
    }
}
