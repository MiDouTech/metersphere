package io.metersphere.agent.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/** MSP-32 v1.7 configuration contract. This namespace never changes legacy execution settings. */
@Component
public class AgentSessionProtocolConfiguration {
    public static final String PREFIX = "agent.session-protocol.";
    private static final Set<String> KEYS = Set.of("enabled", "knowledge-enabled", "migration-enabled", "migration.batch-size", "lease-seconds", "automatic-knowledge-publication",
            "persistent-client-cache", "automatic-verification-expiry", "verification.deadline-seconds",
            "verification.deadline-approval", "limits.title-scalars", "limits.body-scalars",
            "limits.reference-scalars", "limits.content-utf8-bytes", "limits.import-bytes", "limits.evidence-total-bytes", "budget.recoveries",
            "budget.calls", "budget.recovery-seconds", "approval.version", "approval.record",
            "retention.policy-version", "retention.approval-record", "retention.capacity-evidence",
            "retention.reference-protection-evidence", "identity.independence-evidence", "pilot.organization-id",
            "pilot.environment-id", "governance.responsibility-reference");
    private final Environment environment;

    public AgentSessionProtocolConfiguration(Environment environment) {
        this.environment = environment;
    }

    public void requireExecution() {
        if(!executionReadiness().configurationReady())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");
    }
    public void requireKnowledge() {
        var issues=new ArrayList<Issue>();
        encryption(issues);
        boolean enabled=flag("knowledge-enabled",false,issues);
        for(String key:List.of("limits.title-scalars","limits.body-scalars","limits.reference-scalars","limits.content-utf8-bytes"))positive(key,issues);
        for(String key:List.of("approval.version","approval.record","retention.policy-version","retention.approval-record","retention.capacity-evidence","retention.reference-protection-evidence"))reference(key,issues);
        flagMustBeFalse("automatic-knowledge-publication",issues);rejectUnknownKeys(issues);
        if(!enabled||!issues.isEmpty())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");
    }
    public void requireMigration() {
        requireKnowledge();var issues=new ArrayList<Issue>();boolean enabled=flag("migration-enabled",false,issues);positive("migration.batch-size",issues);positive("limits.import-bytes",issues);
        if(!enabled||!issues.isEmpty())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");
    }
    public long number(String key) {
        var issues=new ArrayList<Issue>();integer(key,false,issues);
        if(!issues.isEmpty())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");
        return Long.parseLong(value(key,issues));
    }
    public String reference(String key) {
        var issues=new ArrayList<Issue>();String result=value(key,issues);
        if(result==null||!issues.isEmpty())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");
        return result;
    }
    public Long verificationDeadlineMillis(){
        var issues=new ArrayList<Issue>();if(!flag("automatic-verification-expiry",false,issues)){if(!issues.isEmpty())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");return null;}
        positive("verification.deadline-seconds",issues);reference("verification.deadline-approval",issues);
        if(!issues.isEmpty())throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");
        try{return Math.multiplyExact(number("verification.deadline-seconds"),1000);}catch(ArithmeticException invalid){throw new io.metersphere.sdk.exception.MSException("PROTOCOL_CONFIGURATION_INCOMPLETE");}
    }

    public record Issue(String key, String code) {}
    public record Readiness(boolean requested, List<Issue> issues) {
        public Readiness { issues = List.copyOf(issues); }
        public boolean configurationReady() { return requested && issues.isEmpty(); }
    }

    /** Only names and stable codes leave this validator; never configuration values or policy contents. */
    public Readiness executionReadiness() {
        var issues = new ArrayList<Issue>();
        encryption(issues);
        rejectUnknownKeys(issues);
        boolean requested = flag("enabled", false, issues);
        if (!requested) issues.add(new Issue("enabled", "DISABLED"));
        flagMustBeFalse("automatic-knowledge-publication", issues);
        flagMustBeFalse("persistent-client-cache", issues);
        if (flag("automatic-verification-expiry", false, issues)) {
            positive("verification.deadline-seconds", issues);
            reference("verification.deadline-approval", issues);
        }
        positive("limits.title-scalars", issues);
        positive("limits.body-scalars", issues);
        positive("limits.reference-scalars", issues);
        positive("limits.content-utf8-bytes", issues);
        positive("limits.import-bytes", issues);
        positive("limits.evidence-total-bytes", issues);
        positive("lease-seconds", issues);
        nonNegative("budget.recoveries", issues);
        nonNegative("budget.calls", issues);
        nonNegative("budget.recovery-seconds", issues);
        // These locate evidence, they do not prove that retention/identity protection is implemented.
        // Actual capability availability is checked independently by the protocol service.
        reference("approval.version", issues);
        reference("approval.record", issues);
        reference("retention.policy-version", issues);
        reference("retention.approval-record", issues);
        reference("retention.capacity-evidence", issues);
        reference("retention.reference-protection-evidence", issues);
        reference("identity.independence-evidence", issues);
        reference("pilot.organization-id", issues);
        reference("pilot.environment-id", issues);
        reference("governance.responsibility-reference", issues);
        return new Readiness(requested, issues);
    }

    private void rejectUnknownKeys(List<Issue> issues) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) return;
        Set<String> allowedEnvironmentKeys = KEYS.stream().map(key -> environmentKey(PREFIX + key))
                .collect(java.util.stream.Collectors.toSet());
        try {
            for (var source : configurable.getPropertySources()) {
                if (!(source instanceof EnumerablePropertySource<?> enumerable)) continue;
                for (String name : enumerable.getPropertyNames()) {
                    if ((name.startsWith(PREFIX) && !KEYS.contains(name.substring(PREFIX.length())))
                            || (name.startsWith(environmentKey(PREFIX)) && !allowedEnvironmentKeys.contains(name))) {
                        // Unknown keys themselves may contain secrets, so expose no dynamic field name.
                        issues.add(new Issue("properties", "UNKNOWN_KEY"));
                        return;
                    }
                }
            }
        } catch (RuntimeException unavailable) {
            issues.add(new Issue("properties", "CONFIGURATION_UNAVAILABLE"));
        }
    }

    private String environmentKey(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private boolean flag(String key, boolean fallback, List<Issue> issues) {
        String value = value(key, issues);
        if (value == null) return fallback;
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        issues.add(new Issue(key, "INVALID_BOOLEAN"));
        return false;
    }

    private void flagMustBeFalse(String key, List<Issue> issues) {
        if (flag(key, false, issues)) issues.add(new Issue(key, "UNSUPPORTED_POLICY"));
    }

    private void positive(String key, List<Issue> issues) { integer(key, true, issues); }
    private void nonNegative(String key, List<Issue> issues) { integer(key, false, issues); }

    private void integer(String key, boolean positive, List<Issue> issues) {
        String value = value(key, issues);
        if (value == null) { issues.add(new Issue(key, "REQUIRED")); return; }
        // Long is the storage/measurement representation, not a deployment recommendation.
        if (!value.matches("[0-9]{1,19}")) { issues.add(new Issue(key, "INVALID_INTEGER")); return; }
        BigInteger number = new BigInteger(value);
        if (number.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || (positive && number.signum() == 0))
            issues.add(new Issue(key, "OUT_OF_RANGE"));
    }

    private void reference(String key, List<Issue> issues) {
        if (value(key, issues) == null) issues.add(new Issue(key, "REQUIRED"));
    }
    private void encryption(List<Issue> issues){
        try{String key=environment.getProperty("agent.execution.facts-key");if(key==null||java.util.Base64.getDecoder().decode(key).length!=32)issues.add(new Issue("facts-encryption","REQUIRED"));}
        catch(IllegalArgumentException invalid){issues.add(new Issue("facts-encryption","INVALID_KEY_CONFIGURATION"));}
        catch(RuntimeException unavailable){issues.add(new Issue("facts-encryption","CONFIGURATION_UNAVAILABLE"));}
    }

    private String value(String key, List<Issue> issues) {
        String value;
        try {
            value = environment.getProperty(PREFIX + key);
        } catch (RuntimeException unavailable) {
            // Property sources may include external config; never propagate their raw messages.
            issues.add(new Issue(key, "CONFIGURATION_UNAVAILABLE"));
            return null;
        }
        return value == null || value.isBlank() ? null : value.trim();
    }
}
