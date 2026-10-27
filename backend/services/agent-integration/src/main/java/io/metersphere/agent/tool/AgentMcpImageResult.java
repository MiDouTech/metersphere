package io.metersphere.agent.tool;

/** Explicit protocol content, never inferred from arbitrary business maps. */
public record AgentMcpImageResult(String data, String mimeType, java.util.Map<String,Object> metadata) { }
