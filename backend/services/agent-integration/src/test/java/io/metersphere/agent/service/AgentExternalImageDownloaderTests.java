package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;

class AgentExternalImageDownloaderTests {
    @Test void rejectsUnsafeProtocolsCredentialsPortsAndFragments() {
        for(String url:new String[]{"file:///etc/passwd","ftp://example.com/a","http://user:pass@example.com/a","https://example.com:8080/a","https://example.com/a#x","not-a-url"})
            assertThrows(MSException.class,()->AgentExternalImageDownloader.validateUri(url),url);
        assertEquals("https",AgentExternalImageDownloader.validateUri("https://example.com/image?signature=abc").getScheme());
    }
    @Test void rejectsPrivateMetadataSharedAddressSpaceAndIpv6Variants() throws Exception {
        for(String ip:new String[]{"0.0.0.0","127.0.0.1","10.0.0.1","172.16.0.1","192.168.1.1","169.254.169.254","100.64.0.1","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1","::ffff:127.0.0.1","2001:db8::1"})
            assertThrows(MSException.class,()->AgentExternalImageDownloader.requirePublic(InetAddress.getByName(ip)),ip);
        assertDoesNotThrow(()->AgentExternalImageDownloader.requirePublic(InetAddress.getByName("8.8.8.8")));
        assertDoesNotThrow(()->AgentExternalImageDownloader.requirePublic(InetAddress.getByName("2606:4700:4700::1111")));
    }
    @Test void identifiesActualImageAndSanitizesNameInsteadOfTrustingSourceExtension() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB),"png",out);
        var image=AgentExternalImageDownloader.inspect(out.toByteArray(),"../evidence.html");
        assertEquals("image/png",image.contentType());
        assertTrue(image.fileName().endsWith(".png"));
        assertFalse(image.fileName().contains("/"));
        assertThrows(MSException.class,()->AgentExternalImageDownloader.inspect("<svg></svg>".getBytes(),"image.png"));
        assertThrows(MSException.class,()->AgentExternalImageDownloader.inspect(new byte[0],null));
    }
    @Test void rejectsImagesBeyondPixelBudgetBeforeDecoding() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB),"png",out);
        byte[] image=out.toByteArray();
        // PNG IHDR width/height; reader metadata is checked before full pixel allocation.
        java.nio.ByteBuffer.wrap(image,16,8).putInt(100000).putInt(100000);
        assertThrows(MSException.class,()->AgentExternalImageDownloader.inspect(image,"large.png"));
    }
}
