package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real public-source smoke; offline security tests do not stand in for this check. */
@EnabledIfSystemProperty(named="external.image.network",matches="true")
class AgentExternalImageNetworkTests {
    @Test void downloadsAndDecodesPublicPngWithPinnedDnsAndNormalTlsValidation() {
        var downloader=new AgentExternalImageDownloader();
        try {
            var image=downloader.download("https://httpbin.org/image/png","network-evidence");
            assertEquals("image/png",image.contentType());
            assertTrue(image.bytes().length>0);
            assertEquals("network-evidence.png",image.fileName());
        } finally {downloader.close();}
    }
}
