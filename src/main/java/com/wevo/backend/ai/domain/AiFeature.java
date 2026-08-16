package com.wevo.backend.ai.domain;

import java.util.Locale;

public enum AiFeature {
    ISSUE_DETECTION,
    OPINION_SYNTHESIS,
    DRAFT_GENERATION,
    DRAFT_REVIEW,
    AUTHOR_INTENT_EXTRACTION,
    REVIEW_INTENT_COMPARISON,
    OPINION_CLUSTERING,
    PROJECT_FLOW_REVIEW,
    PROJECT_TITLE_SUGGESTION;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
