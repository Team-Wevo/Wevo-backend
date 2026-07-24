package com.wevo.backend.ai.context;

import java.util.Arrays;

/** canonical bytes와 그 SHA-256 hash. 저장 대상은 hash뿐이다. */
public record AiInputSnapshot(
        byte[] canonicalBytes,
        String inputSnapshotHash
) {

    public AiInputSnapshot {
        canonicalBytes = Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }

    @Override
    public byte[] canonicalBytes() {
        return Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }
}
