package io.metersphere.functional.asset.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CaseAssetHistoryCaseSyncServiceTests {

    @Test
    void eachHistoricalCaseUsesAnIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = CaseAssetHistoryCaseSyncService.class
                .getMethod("sync", String.class, String.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }
}
