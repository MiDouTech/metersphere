package io.metersphere.system.service.department;

import org.apache.commons.lang3.StringUtils;

public final class OrgWecomSyncSecretUtils {

    public static final String MASK_PREFIX = "******";

    private OrgWecomSyncSecretUtils() {
    }

    public static String maskContactSecret(String secret) {
        if (StringUtils.isBlank(secret)) {
            return secret;
        }
        if (secret.length() <= 4) {
            return MASK_PREFIX;
        }
        return MASK_PREFIX + secret.substring(secret.length() - 4);
    }

    public static boolean isMaskedSecret(String secret) {
        return StringUtils.isNotBlank(secret) && secret.startsWith(MASK_PREFIX);
    }
}
