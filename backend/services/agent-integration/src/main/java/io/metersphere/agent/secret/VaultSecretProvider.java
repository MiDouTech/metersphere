package io.metersphere.agent.secret;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class VaultSecretProvider implements AgentSecretProvider {
    @Value("${agent.secret.vault.enabled:false}") private boolean enabled;
    @Value("${agent.secret.vault.address:}") private String address;
    @Value("${agent.secret.vault.token-env:MS_VAULT_TOKEN}") private String tokenEnvironmentVariable;
    @Value("${agent.secret.vault.namespace:}") private String namespace;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Override public String type() { return "VAULT"; }

    @Override
    public void validateReference(String secretRef) {
        if (StringUtils.isBlank(secretRef) || !secretRef.matches("^vault://[A-Za-z0-9_-]+/[A-Za-z0-9_./-]+#[A-Za-z0-9_-]+$")) {
            throw new MSException("CREDENTIAL_SECRET_REF_INVALID");
        }
        if (secretRef.contains("..")) throw new MSException("CREDENTIAL_SECRET_REF_INVALID");
    }

    @Override
    public SecretMetadata verify(String secretRef) {
        VaultValue value = fetch(secretRef);
        return new SecretMetadata(value.version(), null);
    }

    @Override
    public ResolvedSecret resolve(String secretRef, String usernameHint, SecretResolveContext context) {
        VaultValue value = fetch(secretRef);
        return new ResolvedSecret(usernameHint, value.value().toCharArray(), value.version(), null);
    }

    @SuppressWarnings("unchecked")
    private VaultValue fetch(String secretRef) {
        validateReference(secretRef);
        if (!enabled) throw new MSException("VAULT_SECRET_PROVIDER_DISABLED");
        URI base = validateAddress();
        String token = System.getenv(tokenEnvironmentVariable);
        if (StringUtils.isBlank(token)) throw new MSException("VAULT_AUTHENTICATION_UNAVAILABLE");
        String raw = secretRef.substring("vault://".length());
        int slash = raw.indexOf('/'); int hash = raw.lastIndexOf('#');
        String mount = raw.substring(0, slash); String path = raw.substring(slash + 1, hash); String field = raw.substring(hash + 1);
        URI endpoint = base.resolve("/v1/" + mount + "/data/" + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("X-Vault-Token", token).GET();
        if (StringUtils.isNotBlank(namespace)) builder.header("X-Vault-Namespace", namespace);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new MSException("CREDENTIAL_SECRET_REF_UNAVAILABLE");
            Map<String, Object> root = JSON.parseMap(response.body());
            Map<String, Object> outer = (Map<String, Object>) root.get("data");
            Map<String, Object> values = outer == null ? null : (Map<String, Object>) outer.get("data");
            Map<String, Object> metadata = outer == null ? null : (Map<String, Object>) outer.get("metadata");
            Object secret = values == null ? null : values.get(field);
            if (secret == null) throw new MSException("CREDENTIAL_SECRET_REF_UNAVAILABLE");
            return new VaultValue(String.valueOf(secret), metadata == null ? null : String.valueOf(metadata.get("version")));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new MSException("VAULT_REQUEST_INTERRUPTED");
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("CREDENTIAL_SECRET_REF_UNAVAILABLE");
        }
    }

    private URI validateAddress() {
        try {
            URI uri = URI.create(address);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
            return uri;
        } catch (Exception ex) {
            throw new MSException("VAULT_ADDRESS_INVALID");
        }
    }

    private record VaultValue(String value, String version) { }
}
