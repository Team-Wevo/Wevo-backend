package com.wevo.backend.ai.service;

import java.util.UUID;

public record AiGuardrailPendingReservation(String ledgerKey, UUID requestId) {
}
