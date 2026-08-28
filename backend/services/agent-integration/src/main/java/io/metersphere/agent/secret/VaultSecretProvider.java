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
            throw new MSException("Vault 引用格式必须为 vault://mount/path#field");
        }
        if (secretRef.contains("..")) throw new MSException("Vault 引用不允许路径穿越");
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
        if (!enabled) throw new MSException("当前部署未启用 Vault Secret Provider");
        URI base = validateAddress();
        String token = System.getenv(tokenEnvironmentVariable);
        if (StringUtils.isBlank(token)) throw new MSException("Vault 服务身份不可用");
        String raw = secretRef.substring("vault://".length());
        int slash = raw.indexOf('/'); int hash = raw.lastIndexOf('#');
        String mount = raw.substring(0, slash); String path = raw.substring(slash + 1, hash); String field = raw.substring(hash + 1);
        URI endpoint = base.resolve("/v1/" + mount + "/data/" + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("X-Vault-Token", token).GET();
        if (StringUtils.isNotBlank(namespace)) builder.header("X-Vault-Namespace", namespace);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new MSException("Vault 密钥引用不可用");
            Map<String, Object> root = JSON.parseMap(response.body());
            Map<String, Object> outer = (Map<String, Object>) root.get("data");
            Map<String, Object> values = outer == null ? null : (Map<String, Object>) outer.get("data");
            Map<String, Object> metadata = outer == null ? null : (Map<String, Object>) outer.get("metadata");
            Object secret = values == null ? null : values.get(field);
            if (secret == null) throw new MSException("Vault 密钥引用不可用");
            return new VaultValue(String.valueOf(secret), metadata == null ? null : String.valueOf(metadata.get("version")));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new MSException("Vault 请求被中断");
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("Vault 密钥引用不可用");
        }
    }

    private URI validateAddress() {
        try {
            URI uri = URI.create(address);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
            return uri;
        } catch (Exception ex) {
            throw new MSException("Vault 地址必须配置为 HTTPS 地址");
        }
    }

    private record VaultValue(String value, String version) { }
}
