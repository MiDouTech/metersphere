package io.metersphere.functional.handoff;

/** Write-only input; never returned from configuration reads or audit logs. Blank token retains prior value. */
public record HandoffConfiguration(String url,String authMode,String token,boolean clearToken) {
    @Override public String toString(){return "HandoffConfiguration[REDACTED]";}
}
