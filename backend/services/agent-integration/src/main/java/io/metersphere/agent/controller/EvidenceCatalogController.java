package io.metersphere.agent.controller;

import io.metersphere.agent.service.EvidenceCatalogService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping({"/test-assets/evidence","/api/test-assets/evidence"})
public class EvidenceCatalogController {
    private final EvidenceCatalogService service;
    public EvidenceCatalogController(EvidenceCatalogService service){this.service=service;}
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@RequestParam String projectId,@PathVariable String id){
        var content=service.content(projectId,id);
        String imageType=content.inlineImageType();
        var disposition=imageType==null?ContentDisposition.attachment():ContentDisposition.inline();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,disposition.filename(content.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .contentType(imageType==null?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(imageType)).body(content.bytes());
    }
}
