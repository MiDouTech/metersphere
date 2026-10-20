package io.metersphere.agent.service;

import io.metersphere.agent.tool.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentMcpBundleServiceTests {
    private AgentMcpToolHandler tool(String name) {
        return new AgentMcpToolHandler() {
            public String name(){return name;}
            public String requiredScope(){return "TASK_READ";}
            public Map<String,Object> inputSchema(){return Map.of("type","object");}
            public Map<String,Object> annotations(){return Map.of("readOnlyHint",true);}
            public Object execute(Map<String,Object> arguments){return Map.of();}
        };
    }
    @Test void downloadedZipUsesRuntimeCatalogAndChecksums() throws Exception {
        var bundle=new AgentMcpBundleService();
        ReflectionTestUtils.setField(bundle,"registry",new AgentMcpToolRegistry(List.of(tool("metersphere.task.search"),tool("metersphere.functional.submit"),tool("metersphere.execution.trigger.create"))));
        var response=bundle.download();assertEquals("1.4.0",bundle.getManifest().getVersion());
        Map<String,String> files=new LinkedHashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(response.getBody()),StandardCharsets.UTF_8)) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null)files.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));
        }
        String tools=files.get("metersphere-agent/references/tools.md");
        assertTrue(tools.contains("metersphere.task.search"));
        assertFalse(tools.contains("metersphere.functional.submit"));
        assertFalse(tools.contains("metersphere.execution.trigger.create"));
        assertTrue(files.get("metersphere-agent/references/workflows.md").contains("functional.execution.evidence.upload"));
        assertTrue(files.get("metersphere-agent/references/workflows.md").contains("No task, claim, heartbeat or lease"));
        assertTrue(files.values().stream().anyMatch(text->text.contains("NEEDS_REVIEW")));
        String sums=files.get("metersphere-agent/checksums.txt");
        files.forEach((name,body)->{if(!name.endsWith("checksums.txt"))assertTrue(sums.contains(org.apache.commons.codec.digest.DigestUtils.sha256Hex(body)+"  "+name));});
        assertTrue(files.get("metersphere-agent/manifest.json").contains("capabilityDigest"));
    }
}
