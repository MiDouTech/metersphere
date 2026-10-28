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
    private static final String VERSION = "1.4.1";
    @jakarta.annotation.Resource private io.metersphere.agent.tool.AgentMcpToolRegistry registry;
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
            throw new MSException("AGENT_SKILL_PACKAGE_BUILD_FAILED");
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

                Search results with contentError must not be executed. CASE_BODY_INVALID means damaged historical content; CASE_CONTENT_READ_LIMIT means narrow the query and retry. Such rows have no usable steps; inspect top-level warnings instead of assuming zero steps.
                Personal case execution (no task or lease): grant FUNCTIONAL_READ, FUNCTIONAL_SUBMIT and CASE_ATTACHMENT.
                Contract is optional. One PNG/JPEG image per case execution is sufficient. Do not require client contentHash/policyHash or per-step screenshots.
                Upload a case PNG/JPEG with metersphere.functional.execution.evidence.upload (base64, at most 5 MiB) or import_url. Put evidenceIds at submit's top level; steps do not require images.
                Submit with metersphere.functional.execution.submit. Cover every original position at least once; extra and repeated positions are allowed and retained. ACCEPTED/PUBLISHED means structure accepted and declared results saved, not independent execution verification. Historical NEEDS_REVIEW submissions remain unchanged.
                Report actual observations and SUCCESS or ERROR. No comparison with expected text or content reviewer is used. Any ERROR, including extra or repeated records, makes the case ERROR. Permissions, content/policy versions and evidence rules still apply.
                Use metersphere.functional.execution.get to inspect accepted step facts and evidence mapping. Reuse requestId only for identical retries.
                For imports, use functional.import.precheck, inspect its report, then import.commit and import.get. Stop preserves successful writes; retry does not duplicate them. Requires explicit CASE_IMPORT_V2.
                For deletion, use functional.case.delete_preview and let the user decide at confirmationPath before continuing the same previewId. Never forge user confirmation. CASE_DELETE_V2 and CASE_RESTORE_V2 are explicit permissions, not implied by legacy wildcard scopes.
                Archive ended runs with functional.archive.prepare/get/retry. Archival failure never changes the execution verdict. Only submit actual reusable business data through archive.data with sourceObjectId, reuseConditions and cleanupStatus. Requires ARTIFACT_WRITE and current project permissions; no data means no empty dataset.
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
                "capabilityContractVersion", "1.4.0",
                "capabilityDigest", DigestUtils.sha256Hex(toolsMd()),
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
        StringBuilder out = new StringBuilder("# Server capabilities\n\nGenerated from the deployed registry. Call tools/list with your token for the authorized subset.\n\n");
        registry.all().stream().filter(handler -> io.metersphere.agent.tool.AgentMcpToolExposure.discoverable(handler.name()))
                .sorted(java.util.Comparator.comparing(io.metersphere.agent.tool.AgentMcpToolHandler::name))
                .forEach(handler -> out.append("## ").append(handler.name()).append("\n\nScope: ").append(handler.requiredScope())
                        .append("\n\n").append(handler.description()).append("\n\nInput schema:\n```json\n")
                        .append(JSON.toJSONString(handler.inputSchema())).append("\n```\n\n"));
        return out.toString();
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
                `PROJECT_READ` is required for project search/list/get. During migration, `FUNCTIONAL_READ` / `FUNCTIONAL_ALL` temporarily also grant `PROJECT_READ`.
                Empty scopes are denied. Scope matching is exact set membership (no substring match).
                """;
    }

    private String workflowsMd() {
        return """
                # Workflows

                - Search cases before creating duplicates.
                - When the user provides a project name or the numeric ID shown in the MeterSphere project list, call `metersphere.project.search` first (alias `search_projects` is accepted). Use `metersphere.project.list` to enumerate accessible projects. Pass `page`/`pageSize` for pagination; response contains `items`, `total`, `hasMore`. The numeric UI ID maps to `project.num`; if multiple projects share the same number, return all matches and ask the user to choose.
                - For case search, execution result submission, module creation, case creation, and batch case creation, always pass `projectId`. `projectId` may be the internal project id, the numeric UI project ID, or the exact project name; the server resolves it to the internal project id and enforces user/token project access.
                - Personal case execution uses functional.execution.contract -> functional.execution.evidence.upload -> functional.execution.submit -> functional.execution.get. No task, claim, heartbeat or lease is required. Contract is optional. Upload one case image and submit top-level evidenceIds plus actual/result for every original step; no client hashes, per-step images, before/after pairs or expected-text comparison are required. Legacy functional.submit remains forbidden.
                - The advanced Runner task channel remains compatible with its existing lease protocol; its lease requirements do not apply to direct personal case execution.
                - Reuse the same requestId and identical payload for retries. IDEMPOTENCY_CONFLICT requires checking the existing operation; do not generate another key to bypass it.
                - Case-library search accepts projectId and optional identityType (ID/NUMBER/NAME). Omit testPlanId to search the repository; inspect current/pageSize/total/hasMore. Live paging is not a frozen export snapshot.
                - Read case updateTime before case.update; keep customFields as a field-ID map. A conflict requires rereading, not overwriting another writer.
                - functional.comments.list returns {total,current,pageSize,list}: page roots with current/pageSize, or replies with parentId. Read updateTime before mutation. Edit only your own text with explicit CASE_COMMENT_EDIT_V2. Deletion requires explicit CASE_COMMENT_DELETE_V2, expectedUpdateTime and requestId, and leaves a tombstone preserving replies. Neither scope is implied by AGENT_ALL. Invalid credentials must not fall back to a browser session. Personal tokens cannot manage credentials.
                - Use module.get to read updateTime before module.update/move. Moving to parentId=NONE means project root. CASE_MODULE_WRITE_V2 is an explicit grant; AGENT_ALL and FUNCTIONAL_ALL do not include it.
                - Never infer that deletion/import/export is available from this guide. Only invoke capabilities exposed by live tools/list. Running-case deletion must wait for the user's documented decision when that capability becomes available.
                - Review create/associate require requestId; read the review after a response loss. Review decisions and Excel import are not promised unless the live tools/list exposes them.
                - For bug creation, include reproduction steps, expected result, actual result, severity, and related case ID when known.
                """;
    }

    private String troubleshootingMd() {
        return """
                # Troubleshooting

                - 401: token missing, expired, disabled, or malformed on POST.
                - GET /api/mcp returns 405 (no SSE); clients must use POST Streamable HTTP.
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
