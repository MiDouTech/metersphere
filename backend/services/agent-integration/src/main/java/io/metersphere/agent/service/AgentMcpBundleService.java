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
    private static final String VERSION = "1.5.0";
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
        files.put("metersphere-agent/references/business-assets.json", businessAssetProtocol());
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
                ---
                name: metersphere-agent
                description: Search and maintain MeterSphere cases, plans, reviews and bugs, and submit personal execution evidence through remote MCP.
                ---
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

                Business understanding follows the linked user's current project roles; no separate Token feature grant is needed.
                Connect with metersphere.executor.connect and follow its assetProtocol JSON. Read pinned versions with executor.context.get before exploring.
                After exploration or execution, upload reusable understanding with business_asset.upload. Same module or related flow uses one document; operations are branches. Update the existing asset with baseVersion, preserve evidence references, distinguish observed facts, inference and unresolved questions. Never upload credentials into business documents.
                Report progress/deviations with executor.event; poll executor.commands and respond APPLIED/FAILED. Explicit context replacement uses executor.context.refresh with reason and expectedVersionId. No frozen action plan or legacy Runner lease is required.

                Search results with contentError must not be executed. CASE_BODY_INVALID means damaged historical content; CASE_CONTENT_READ_LIMIT means narrow the query and retry. Such rows have no usable steps; inspect top-level warnings instead of assuming zero steps.
                Personal case execution (no task or lease) follows the linked user current case execution and evidence permissions.
                Contract is optional. One PNG/JPEG image per case execution is sufficient. Do not require client contentHash/policyHash or per-step screenshots.
                Upload a case PNG/JPEG with metersphere.functional.execution.evidence.upload (base64, at most 5 MiB) or import_url. Put evidenceIds at submit's top level; steps do not require images.
                Submit with metersphere.functional.execution.submit. Cover every original position at least once; extra and repeated positions are allowed and retained. ACCEPTED/PUBLISHED means structure accepted and declared results saved, not independent execution verification. Historical NEEDS_REVIEW submissions remain unchanged.
                Report actual observations and SUCCESS or ERROR. No comparison with expected text or content reviewer is used. Any ERROR, including extra or repeated records, makes the case ERROR. Permissions, content/policy versions and evidence rules still apply.
                Use metersphere.functional.execution.get to inspect accepted step facts and evidence mapping. Reuse requestId only for identical retries.
                For imports, use functional.import.precheck, inspect its report, then import.commit and import.get. Stop preserves successful writes; retry does not duplicate them. Requires the linked user's current case import permission.
                For deletion, use functional.case.delete_preview and let the user decide at confirmationPath before continuing the same previewId. Never forge user confirmation. Deletion and restoration follow current user roles and the original confirmation/version rules; no extra Token grant is required.
                Archive ended runs with functional.archive.prepare/get/retry. Archival failure never changes the execution verdict. Only submit actual reusable business data through archive.data with sourceObjectId, reuseConditions and cleanupStatus. Requires current user project permissions; no data means no empty dataset.
                """;
    }

    private String readme() {
        return """
                # MeterSphere Agent Skill Package

                1. In MeterSphere, open Personal Center > Agent Configuration.
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
                "capabilityContractVersion", "1.4.2",
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
        StringBuilder out = new StringBuilder("# Server capabilities\n\nGenerated from the deployed registry. Call tools/list for the complete public catalog. Every call checks the linked user current roles and project/resource access.\n\n");
        registry.all().stream().filter(handler -> io.metersphere.agent.tool.AgentMcpToolExposure.discoverable(handler.name()))
                .sorted(java.util.Comparator.comparing(io.metersphere.agent.tool.AgentMcpToolHandler::name))
                .forEach(handler -> out.append("## ").append(handler.name()).append("\n\nAuthorization: linked user current roles (USER_RBAC)")
                        .append("\n\n").append(handler.description()).append("\n\nInput schema:\n```json\n")
                        .append(JSON.toJSONString(handler.inputSchema())).append("\n```\n\n"));
        return out.toString();
    }

    private String permissionsMd() {
        return """
                # Permissions

                Runtime permission is the intersection of:

                - MeterSphere user RBAC
                - Current linked-user identity and Token validity
                - Project allow-list
                - Server-side tool policy

                All MCP capabilities, including new tools, follow the linked user's current roles. Historical Token scopes do not impose a second feature authorization layer. Existing Tokens require no reconfiguration; project allow-lists and resource ownership still apply.
                Project search/list/get use current user access and the Token project allow-list.
                Token expiry, revocation, user role changes, resource ownership and business confirmations are checked independently of legacy scope metadata.
                """;
    }

    private String workflowsMd() {
        return """
                # Workflows

                - Business understanding: executor.connect -> executor.context.get -> explore/execute -> executor.evidence -> business_asset.upload. Use source returned by connect. Same module or related flow stays in one document with operation branches. Keep the previous version and submit full updated content with baseVersion. CONFLICT means content was retained for deliberate merge, never silently overwrite it.
                - Monitor/correct: report executor.event observations, poll executor.commands, respond with executor.respond. If a business change requires new context during execution, use executor.context.refresh explicitly with a reason; it records the replacement and notifies the executor.
                - Shared configuration: list metadata and references from the platform. An authorized executor resolves actual values through the session-bound configuration endpoint. Never paste passwords into understanding documents, screenshots, prompts, ordinary events or logs.

                - Search cases before creating duplicates.
                - When the user provides a project name or the numeric ID shown in the MeterSphere project list, call `metersphere.project.search` first (alias `search_projects` is accepted). Use `metersphere.project.list` to enumerate accessible projects. Pass `page`/`pageSize` for pagination; response contains `items`, `total`, `hasMore`. The numeric UI ID maps to `project.num`; if multiple projects share the same number, return all matches and ask the user to choose.
                - For case search, execution result submission, module creation, case creation, and batch case creation, always pass `projectId`. `projectId` may be the internal project id, the numeric UI project ID, or the exact project name; the server resolves it to the internal project id and enforces user/token project access.
                - Personal case execution uses functional.execution.contract -> functional.execution.evidence.upload -> functional.execution.submit -> functional.execution.get. No task, claim, heartbeat or lease is required. Contract is optional. Upload one case image and submit top-level evidenceIds plus actual/result for every original step; no client hashes, per-step images, before/after pairs or expected-text comparison are required. Legacy functional.submit remains forbidden.
                - The advanced Runner task channel remains compatible with its existing lease protocol; its lease requirements do not apply to direct personal case execution.
                - Reuse the same requestId and identical payload for retries. IDEMPOTENCY_CONFLICT requires checking the existing operation; do not generate another key to bypass it.
                - Case-library search accepts projectId and optional identityType (ID/NUMBER/NAME). Omit testPlanId to search the repository; inspect current/pageSize/total/hasMore. Live paging is not a frozen export snapshot.
                - Read case updateTime before case.update; keep customFields as a field-ID map. A conflict requires rereading, not overwriting another writer.
                - functional.comments.list returns {total,current,pageSize,list}: page roots with current/pageSize, or replies with parentId. Read updateTime before mutation. Edit only your own text with the current user's comment permission. Deletion requires current user permissions, expectedUpdateTime and requestId, and leaves a tombstone preserving replies. Invalid credentials must not fall back to a browser session. Personal tokens cannot manage credentials.
                - Use module.get to read updateTime before module.update/move. Moving to parentId=NONE means project root. Module management follows the linked user's current case permissions; no separate Token grant is needed.
                - Never infer that deletion/import/export is available from this guide. Only invoke capabilities exposed by live tools/list. Running-case deletion must wait for the user's documented decision when that capability becomes available.
                - Review create/associate require requestId; read the review after a response loss. Review decisions and Excel import are not promised unless the live tools/list exposes them.
                - For bug creation, inspect the current template and required fields; use published workflow initial status rather than inventing one. Include reproduction, expected and actual results when supported by the template.
                - Case creation: use a nonblank name of at most 255 characters and a priority allowed by the current template. Batch creation may partially succeed: inspect every row and retry identical requests with their original keys.
                - Upload preparation/completion is purpose-specific: use bug.attachment.upload.prepare/complete for bug detail or comment attachments, and functional.attachment.upload.prepare/complete for case attachments. An upload intent is not an execution evidence ID.
                - Bug association write results include relation IDs; preserve these for disassociation rather than substituting library case IDs.
                - If live tools/list exposes metersphere.bug.export.download, read the export fileId in chunks (length default 32768, maximum 65536), advancing nextOffset until eof. Assemble binary data in the client transport, verify sizeBytes and sha256, then ZIP CRC and Excel content. Each chunk requires current ownership, export permission and unexpired BUG_EXPORT purpose. Do not place entire ZIP base64 in model context. This repository implementation still requires runtime verification; older deployments may not expose it.
                - Export retry is only for failed, stopped or expired jobs, never successful jobs. Optional NULL custom fields must not be interpreted as invalid execution data.
                """;
    }

    private String troubleshootingMd() {
        return """
                # Troubleshooting

                - 401: token missing, expired, disabled, or malformed on POST.
                - GET /api/mcp returns 405 (no SSE); clients must use POST Streamable HTTP.
                - 403: token scope, user RBAC, or project allow-list does not permit the operation.
                - 429 or error.data.code=AGENT_RATE_LIMITED: token rate limit exceeded. Inspect error.data.details.retryAfterMs and nextAction when present. CONFIGURED_WINDOW_UPPER_BOUND is a conservative suggestion, not an exact reset time.
                - HTTP 503 with Retry-After: admission is busy. Use bounded backoff and jitter; keep write requestId and payload unchanged. Start with client concurrency 2.
                - Preserve traceId for diagnosis; never treat requestId, taskId and traceId as interchangeable.
                - VERSION_CONFLICT: reread and compare before changing fields. Unknown failures are not proof that a write did not happen.
                - operation.get currently covers the personal execution operations exposed by its live schema; do not assume generic create/query recovery has been implemented.
                - Server capabilities, review search/update/disassociation and generic operation-query extensions in implementation plans are not guaranteed live tools. Use tools/list as the authority.
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
    private String businessAssetProtocol() {
        return JSON.toJSONString(Map.ofEntries(
            Map.entry("schemaVersion","1.0"),Map.entry("connectTool","metersphere.executor.connect"),
            Map.entry("readTool","metersphere.executor.context.get"),Map.entry("uploadTool","metersphere.business_asset.upload"),
            Map.entry("source","Use assetProtocol.source from the authenticated connection response"),
            Map.entry("grouping","One document per module or related business flow; different operations are branches"),
            Map.entry("versioning","Read current version, submit baseVersion, retain conflicts and all historical versions"),
            Map.entry("sections",java.util.List.of("业务范围","业务目标","前置条件","操作分支","关联流程","业务规则","理解依据","待确认内容","本次变化")),
            Map.entry("secrets","Use shared configuration references; never upload credential values")));
    }
}
