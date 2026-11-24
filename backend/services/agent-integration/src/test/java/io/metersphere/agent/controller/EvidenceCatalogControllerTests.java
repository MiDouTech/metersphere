package io.metersphere.agent.controller;
import io.metersphere.agent.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceCatalogControllerTests {
    @Test void activeLegacyContentMustBeDownloadedAndNeverExecuteInline(){
        var service=mock(EvidenceCatalogService.class);var controller=new EvidenceCatalogController(service);
        byte[] html="<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.content("project","old")).thenReturn(new EvidenceCatalogService.Content("proof.html","text/html",html));
        var response=controller.content("project","old");assertArrayEquals(html,response.getBody());
        assertEquals("application/octet-stream",response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
        assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));assertEquals("no-store",response.getHeaders().getCacheControl());
    }
    @Test void onlyValidatedOriginalImagesCanBePreviewedAndUnauthorizedReadsFail(){
        var service=mock(EvidenceCatalogService.class);var controller=new EvidenceCatalogController(service);
        var png=EvidenceCatalogServiceTests.png();
        when(service.content("project","proof")).thenReturn(new EvidenceCatalogService.Content("proof.png","text/html",png));
        var response=controller.content("project","proof");assertTrue(response.getHeaders().getContentDisposition().isInline());
        assertEquals("image/png",response.getHeaders().getContentType().toString());assertArrayEquals(png,response.getBody());
        when(service.content("other","proof")).thenThrow(AgentBusinessAccess.denied());
        assertThrows(RuntimeException.class,()->controller.content("other","proof"));
    }
}
