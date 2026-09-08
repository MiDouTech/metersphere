package io.metersphere.agent.secret;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretProvider implements AgentSecretProvider {
    @Value("${agent.secret.env-enabled:false}")
    private boolean enabled;

    @Override public String type() { return "ENV"; }

    @Override
    public void validateReference(String secretRef) {
        if (!secretRef.matches("^env://[A-Z][A-Z0-9_]{1,127}$")) {
            throw new MSException("CREDENTIAL_SECRET_REF_INVALID");
        }
    }

    @Override
    public SecretMetadata verify(String secretRef) {
        validateReference(secretRef);
        if (!enabled) throw new MSException("ENV_SECRET_PROVIDER_DISABLED");
        String variable = secretRef.substring("env://".length());
        if (StringUtils.isBlank(System.getenv(variable))) throw new MSException("CREDENTIAL_SECRET_REF_UNAVAILABLE");
        return new SecretMetadata("environment", null);
    }

    @Override
    public ResolvedSecret resolve(String secretRef, String usernameHint, SecretResolveContext context) {
        SecretMetadata metadata = verify(secretRef);
        String value = System.getenv(secretRef.substring("env://".length()));
        return new ResolvedSecret(usernameHint, value.toCharArray(), metadata.version(), metadata.expiresAt());
    }
}
