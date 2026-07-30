package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentMcpManifestDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AgentMcpBundleService {
    private static final String PACKAGE_NAME = "metersphere-agent-skill";
    private static final String VERSION = "1.0.0";
    private static final String FILE_NAME = PACKAGE_NAME + "-" + VERSION + ".zip";
    private static final String TEST_BASE_URL = "https://msp.ebcone.net";
    private static final String PROD_BASE_URL = "https://msp.ebcone.cn";

    public AgentMcpManifestDTO getManifest() {
        AgentMcpManifestDTO dto = new AgentMcpManifestDTO();
        dto.setName(PACKAGE_NAME);
        dto.setVersion(VERSION);
        dto.setFileName(FILE_NAME);
        dto.setDescription("MeterSphere AI Skill package for remote Streamable HTTP MCP. Platform addresses are embedded; no token is embedded.");
        dto.setNodeEngine("not-required");
        dto.setInstallHint("Create a personal Agent Token, then configure your AI client to call the test or production /api/mcp endpoint with Bearer or X-API-Key.");
        dto.setAvailable(true);
        return dto;
    }

    public ResponseEntity<byte[]> download() {
        try {
            byte[] bytes = buildSkillPackage();
            String encoded = URLEncoder.encode(FILE_NAME, StandardCharsets.UTF_8).replace("+", "%20");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            headers.setContentLength(bytes.length);
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            throw new MSException("Failed to build Agent skill package: " + e.getMessage());
        }
    }

    private byte[] buildSkillPackage() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("metersphere-agent/SKILL.md", skillMd());
        files.put("metersphere-agent/README.md", readme());
        files.put("metersphere-agent/manifest.json", manifestJson());
        files.put("metersphere-agent/references/tools.md", toolsMd());
        files.put("metersphere-agent/references/platforms.md", platformsMd());
        files.put("metersphere-agent/references/permissions.md", permissionsMd());
        files.put("metersphere-agent/references/workflows.md", workflowsMd());
        files.put("metersphere-agent/references/troubleshooting.md", troubleshootingMd());
        files.put("metersphere-agent/examples/codex.config.example.toml", codexExample());
        files.put("metersphere-agent/examples/codex.test.config.example.toml", codexExample(TEST_BASE_URL));
        files.put("metersphere-agent/examples/codex.prod.config.example.toml", codexExample(PROD_BASE_URL));
        files.put("metersphere-agent/examples/chatgpt-remote-mcp.test.example.json", remoteMcpExample("ChatGPT", TEST_BASE_URL, "test"));
        files.put("metersphere-agent/examples/chatgpt-remote-mcp.prod.example.json", remoteMcpExample("ChatGPT", PROD_BASE_URL, "prod"));
        files.put("metersphere-agent/examples/cursor.remote-mcp.test.example.json", remoteMcpExample("Cursor", TEST_BASE_URL, "test"));
        files.put("metersphere-agent/examples/cursor.remote-mcp.prod.example.json", remoteMcpExample("Cursor", PROD_BASE_URL, "prod"));
        files.put("metersphere-agent/examples/workbuddy-mcp.test.example.json", remoteMcpExample("WorkBuddy", TEST_BASE_URL, "test"));
        files.put("metersphere-agent/examples/workbuddy-mcp.prod.example.json", remoteMcpExample("WorkBuddy", PROD_BASE_URL, "prod"));
        files.put("metersphere-agent/examples/generic-streamable-http.example.json", remoteMcpExample("Generic"));
        files.put("metersphere-agent/scripts/verify-mcp-connection.js", verifyScript());
        files.put("metersphere-agent/checksums.txt", checksums(files));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private String skillMd() {
        return """
                # MeterSphere Agent

                Use this skill when the user asks you to search, create, update, or submit MeterSphere test cases, test execution results, test plans, reviews, or bugs through the MeterSphere remote MCP endpoint.

                The skill never contains a real token. Ask the user to create a personal Agent Token in MeterSphere and configure the client with `Authorization: Bearer ${METERSPHERE_AGENT_TOKEN}` or `X-API-Key: ${METERSPHERE_AGENT_TOKEN}`.

                Built-in platform addresses:

                - Test: `https://msp.ebcone.net`
                - Production: `https://msp.ebcone.cn`

                Default MCP endpoints:

                - Test: `https://msp.ebcone.net/api/mcp`
                - Production: `https://msp.ebcone.cn/api/mcp`

                Only ask the user for a base URL when they are using a non-standard private deployment.
                Transport: Streamable HTTP
                """;
    }

    private String readme() {
        return """
                # MeterSphere Agent Skill Package

                1. In MeterSphere, open System Settings > Agent Integration.
                2. Create a personal Agent Token. The plaintext token is displayed only once.
                3. Configure your AI client with one of the built-in remote MCP endpoints:
                   - Test: `https://msp.ebcone.net/api/mcp`
                   - Production: `https://msp.ebcone.cn/api/mcp`
                4. Provide the token through an environment variable or secret store. Do not commit tokens.

                If you use a private deployment, set `METERSPHERE_BASE_URL` to that deployment URL and call `${METERSPHERE_BASE_URL}/api/mcp`.
                """;
    }

    private String manifestJson() {
        return JSON.toJSONString(Map.of(
                "name", PACKAGE_NAME,
                "version", VERSION,
                "transport", "streamable-http",
                "endpoint", "/api/mcp",
                "platforms", Map.of(
                        "test", TEST_BASE_URL,
                        "production", PROD_BASE_URL
                ),
                "tokenEmbedded", false
        ));
    }

    private String platformsMd() {
        return """
                # Platform addresses

                Use these built-in platform addresses unless the user explicitly says they are using a private deployment:

                - Test environment: `https://msp.ebcone.net`
                - Production environment: `https://msp.ebcone.cn`

                MCP endpoints:

                - Test: `https://msp.ebcone.net/api/mcp`
                - Production: `https://msp.ebcone.cn/api/mcp`

                Tokens are never embedded in this package. The user must create a personal Agent Token in MeterSphere and configure it as a client secret or environment variable.
                """;
    }

    private String toolsMd() {
        return """
                # Tools

                - metersphere.functional.search
                - metersphere.functional.get
                - metersphere.functional.modules
                - metersphere.functional.submit
                - metersphere.functional.module.create
                - metersphere.functional.case.create
                - metersphere.functional.case.batch_create
                - metersphere.bug.search
                - metersphere.bug.get
                - metersphere.bug.create
                - metersphere.bug.update
                - metersphere.project.create
                - metersphere.project.members.add
                - metersphere.project.get
                - metersphere.test_plan.create
                - metersphere.test_plan.associate_cases
                - metersphere.test_plan.get
                - metersphere.case_review.create
                - metersphere.case_review.associate_cases
                - metersphere.case_review.get
                """;
    }

    private String permissionsMd() {
        return """
                # Permissions

                Runtime permission is the intersection of:

                - MeterSphere user RBAC
                - Agent Token scopes
                - Project allow-list
                - Server-side tool policy

                `AGENT_ALL` grants all Agent scopes but never bypasses the bound user's RBAC or project restrictions.
                """;
    }

    private String workflowsMd() {
        return """
                # Workflows

                - Search cases before creating duplicates.
                - When submitting execution results, include exact case ID, result, step snapshots, and attachments where available.
                - For bug creation, include reproduction steps, expected result, actual result, severity, and related case ID when known.
                """;
    }

    private String troubleshootingMd() {
        return """
                # Troubleshooting

                - 401: token missing, expired, disabled, or malformed.
                - 403: token scope, user RBAC, or project allow-list does not permit the operation.
                - 429: token rate limit exceeded.
                - Do not paste tokens into prompts; configure them as environment variables or client secrets.
                """;
    }

    private String codexExample() {
        return """
                [mcp_servers.metersphere]
                type = "streamable-http"
                url = "${METERSPHERE_BASE_URL}/api/mcp"

                [mcp_servers.metersphere.headers]
                Authorization = "Bearer ${METERSPHERE_AGENT_TOKEN}"
                """;
    }

    private String codexExample(String baseUrl) {
        return """
                [mcp_servers.metersphere]
                type = "streamable-http"
                url = "%s/api/mcp"

                [mcp_servers.metersphere.headers]
                Authorization = "Bearer ${METERSPHERE_AGENT_TOKEN}"
                """.formatted(baseUrl);
    }

    private String remoteMcpExample(String client) {
        return JSON.toJSONString(Map.of(
                "name", "metersphere",
                "client", client,
                "transport", "streamable-http",
                "url", "${METERSPHERE_BASE_URL}/api/mcp",
                "headers", Map.of("Authorization", "Bearer ${METERSPHERE_AGENT_TOKEN}")
        ));
    }

    private String remoteMcpExample(String client, String baseUrl, String environment) {
        return JSON.toJSONString(Map.of(
                "name", "metersphere",
                "client", client,
                "environment", environment,
                "transport", "streamable-http",
                "url", baseUrl + "/api/mcp",
                "headers", Map.of("Authorization", "Bearer ${METERSPHERE_AGENT_TOKEN}")
        ));
    }

    private String verifyScript() {
        return """
                const platforms = {
                  test: 'https://msp.ebcone.net',
                  production: 'https://msp.ebcone.cn',
                  prod: 'https://msp.ebcone.cn',
                };
                const env = (process.env.METERSPHERE_ENV || 'test').toLowerCase();
                const baseUrl = process.env.METERSPHERE_BASE_URL || platforms[env];
                const token = process.env.METERSPHERE_AGENT_TOKEN;
                if (!baseUrl || !token) {
                  throw new Error('Set METERSPHERE_AGENT_TOKEN first. Set METERSPHERE_ENV=production or METERSPHERE_BASE_URL only when needed.');
                }
                const res = await fetch(`${baseUrl.replace(/\\/$/, '')}/api/mcp`, {
                  method: 'POST',
                  headers: {
                    'content-type': 'application/json',
                    authorization: `Bearer ${token}`,
                  },
                  body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'ping' }),
                });
                console.log(res.status, await res.text());
                """;
    }

    private String checksums(Map<String, String> files) {
        StringBuilder builder = new StringBuilder();
        files.forEach((path, content) -> {
            if (!StringUtils.endsWith(path, "checksums.txt")) {
                builder.append(DigestUtils.sha256Hex(content)).append("  ").append(path).append('\n');
            }
        });
        return builder.toString();
    }
}
