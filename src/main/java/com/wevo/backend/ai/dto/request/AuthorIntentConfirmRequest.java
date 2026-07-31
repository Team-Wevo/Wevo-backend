package com.wevo.backend.ai.dto.request;

import com.wevo.backend.section.domain.AuthorIntentTextPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AuthorIntentConfirmRequest(
        @NotNull @Positive Integer contentVersion,
        @NotBlank @Size(max = AuthorIntentTextPolicy.MAX_LENGTH) String intent
) {
}
