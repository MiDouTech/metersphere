package io.metersphere.agent.security;

/** Reject credential-shaped content before storing business documents, events or audit reasons. */
public final class BusinessAssetSafety {
  private BusinessAssetSafety() {}

  public static boolean containsSecret(String value) {
    return value != null
        && (AgentSensitiveDataSanitizer.containsSensitive(value)
            || value.matches("(?s).*(?:密码|口令|密钥)\\s*[:：=]\\s*[^\\s]+.*")
            || value.matches("(?is).*https?://[^\\s/@]+:[^\\s/@]+@.*"));
  }
}
