package io.metersphere.agent.constants;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum AgentAttachmentPurpose {
    CASE_DETAIL,
    CASE_COMMENT,
    BUG_DETAIL,
    BUG_COMMENT,
    EXECUTION;

    private static final Set<String> NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    public static AgentAttachmentPurpose from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!NAMES.contains(normalized)) {
            return null;
        }
        return AgentAttachmentPurpose.valueOf(normalized);
    }

    public boolean isCaseDetail() {
        return this == CASE_DETAIL;
    }

    public boolean isBugDetail() {
        return this == BUG_DETAIL;
    }

    public boolean isCaseComment() {
        return this == CASE_COMMENT;
    }

    public boolean isBugComment() {
        return this == BUG_COMMENT;
    }

    public boolean isExecution() {
        return this == EXECUTION;
    }
}
