package io.metersphere.system.service.department;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrgWecomSyncSecretUtilsTest {

    @Test
    void maskContactSecret_showsLastFourChars() {
        Assertions.assertEquals("******cret", OrgWecomSyncSecretUtils.maskContactSecret("mysecret"));
    }

    @Test
    void isMaskedSecret_detectsPlaceholder() {
        Assertions.assertTrue(OrgWecomSyncSecretUtils.isMaskedSecret("******cret"));
        Assertions.assertFalse(OrgWecomSyncSecretUtils.isMaskedSecret("mysecret"));
    }
}
