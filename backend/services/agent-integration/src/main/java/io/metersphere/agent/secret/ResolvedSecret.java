package io.metersphere.agent.secret;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

import java.util.Arrays;

@JsonIgnoreType
public final class ResolvedSecret implements AutoCloseable {
    private final String username;
    private final char[] value;
    private final String version;
    private final Long expiresAt;

    public ResolvedSecret(String username, char[] value, String version, Long expiresAt) {
        this.username = username;
        this.value = value == null ? new char[0] : value.clone();
        this.version = version;
        this.expiresAt = expiresAt;
    }
    public String username() { return username; }
    public char[] valueCopy() { return value.clone(); }
    public String version() { return version; }
    public Long expiresAt() { return expiresAt; }
    @Override public void close() { Arrays.fill(value, '\0'); }
    @Override public String toString() { return "ResolvedSecret[REDACTED]"; }
}
