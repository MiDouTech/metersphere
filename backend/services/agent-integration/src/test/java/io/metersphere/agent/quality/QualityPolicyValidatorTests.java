package io.metersphere.agent.quality;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QualityPolicyValidatorTests {
    static final String VALID = """
            {"schemaVersion":"quality-policy.v1","name":"Policy","rules":[
              {"ruleId":"Q-EVIDENCE-01","parameters":{"requiredRoles":["AFTER_ACTION"],"allowedMimeTypes":["image/png"],"maxArtifactBytes":1024}},
              {"ruleId":"Q-ASSERT-01","parameters":{"allowedOperators":["EQUALS"],"expectedSource":"FROZEN_CONTRACT"}}
            ]}
            """;
    private final QualityPolicyValidator validator = new QualityPolicyValidator();

    @Test void validPolicyHasStableCanonicalDigest() {
        var first = validator.validate(VALID);
        var reordered = VALID.replace("\"schemaVersion\":\"quality-policy.v1\",\"name\":\"Policy\"",
                "\"name\":\"Policy\",\"schemaVersion\":\"quality-policy.v1\"");
        assertTrue(first.valid());
        assertEquals(64, first.contentHash().length());
        assertEquals(first.contentHash(), validator.validate(reordered).contentHash());
    }
    @Test void cannotDisableMandatoryRulesOrInjectScripts() {
        for (String field : new String[]{"enabled", "source", "approvalStatus", "script", "qualityMode"}) {
            assertFalse(validator.validate(VALID.replace("\"name\":\"Policy\"", "\"name\":\"Policy\",\""+field+"\":false")).valid());
        }
    }
    @Test void rejectsDuplicatePropertiesAndTrailingDocuments() {
        assertFalse(validator.validate(VALID.replace("\"name\":\"Policy\"", "\"name\":\"Policy\",\"name\":\"Other\"")).valid());
        assertFalse(validator.validate(VALID + " {}").valid());
        assertFalse(validator.validate("/* comment */" + VALID).valid());
    }
    @Test void rejectsOutOfBoundsAndWrongTypes() {
        for (String value : new String[]{"0", "10485761", "1.5", "\"1024\"", "null"}) {
            assertFalse(validator.validate(VALID.replace(":1024", ":"+value)).valid(), value);
        }
        assertFalse(validator.validate(VALID.replace("image/png", "text/html")).valid());
        assertFalse(validator.validate(VALID.replace("EQUALS", "EVAL_SCRIPT")).valid());
        assertFalse(validator.validate(VALID.replace("FROZEN_CONTRACT", "SUBMISSION")).valid());
    }
    @Test void rejectsMissingAndDuplicateRules() {
        assertFalse(validator.validate("{\"schemaVersion\":\"quality-policy.v1\",\"name\":\"P\",\"rules\":[]}").valid());
        String evidence = "{\"ruleId\":\"Q-EVIDENCE-01\",\"parameters\":{\"requiredRoles\":[\"AFTER_ACTION\"],\"allowedMimeTypes\":[\"image/png\"],\"maxArtifactBytes\":1}}";
        assertFalse(validator.validate("{\"schemaVersion\":\"quality-policy.v1\",\"name\":\"P\",\"rules\":["+evidence+","+evidence+"]}").valid());
    }
    @Test void limitsSizeDepthAndSchemaVersions() {
        assertFalse(validator.validate(" ".repeat(16385)).valid());
        assertFalse(validator.validate("[".repeat(13)+"0"+"]".repeat(13)).valid());
        assertFalse(validator.validate(VALID.replace("quality-policy.v1", "quality-policy.v2")).valid());
        assertFalse(validator.validate(VALID.replace("Policy", "   ")).valid());
    }
    @Test void invalidResultNeverIncludesUntrustedRawExceptionText() {
        var invalid = validator.validate("{\"name\":\"secret_password\"");
        assertFalse(invalid.valid());
        assertNull(invalid.contentHash());
        assertNull(invalid.normalizedJson());
        assertFalse(invalid.errors().toString().contains("secret_password"));
    }
    @Test void locatesNestedParameterErrorsWithoutEchoingValues() {
        var oversized = validator.validate(VALID.replace(":1024", ":99999999"));
        assertTrue(oversized.errors().stream().anyMatch(e -> e.path().equals("/rules/0/parameters/maxArtifactBytes") && e.code().equals("SCHEMA_MAXIMUM")));
        var operator = validator.validate(VALID.replace("EQUALS", "secret_script"));
        assertTrue(operator.errors().stream().anyMatch(e -> e.path().equals("/rules/1/parameters/allowedOperators/0")));
        assertFalse(operator.errors().toString().contains("secret_script"));
        var missing = validator.validate(VALID.replace("\"expectedSource\":\"FROZEN_CONTRACT\"", "\"unexpected\":true"));
        assertTrue(missing.errors().stream().anyMatch(e -> e.path().equals("/rules/1/parameters/expectedSource") && e.code().equals("FIELD_REQUIRED")));
        assertTrue(missing.errors().stream().anyMatch(e -> e.path().equals("/rules/1/parameters/unexpected") && e.code().equals("FIELD_UNKNOWN")));
    }
}
