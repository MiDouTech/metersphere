package io.metersphere.agent.quality;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TypedAssertionComparatorTests {
    private final ObjectMapper json=new ObjectMapper();
    @Test void exactDecimalToleranceAndBoundsWithoutDouble() throws Exception {
        var rule=json.readTree("{\"contractVersion\":\"v2\",\"assertionId\":\"a1\",\"valueType\":\"DECIMAL\",\"precision\":38,\"scale\":2,\"operator\":\"EQUALS\",\"expected\":\"9007199254740993.10\"}");
        assertTrue(TypedAssertionComparator.compare(rule,TextNode.valueOf("9007199254740993.1")));
        assertFalse(TypedAssertionComparator.compare(rule,TextNode.valueOf("9007199254740993.11")));
        for(String invalid:List.of("NaN","Infinity","1e3","1.001"))assertThrows(MSException.class,()->TypedAssertionComparator.compare(rule,TextNode.valueOf(invalid)));
        assertThrows(MSException.class,()->TypedAssertionComparator.compare(rule,json.valueToTree(1.1)));
    }
    @Test void v2MatchesByIdentityAndRejectsDuplicatesUnknownIdsAndForgedExpected() throws Exception {
        var policy=json.readTree("{\"rules\":[{\"ruleId\":\"Q-ASSERT-01\",\"parameters\":{\"allowedOperators\":[\"EQUALS\"]}}]}");
        var frozen=json.readTree("[{\"contractVersion\":\"v2\",\"assertionId\":\"a1\",\"valueType\":\"STRING\",\"operator\":\"EQUALS\",\"expected\":\"first\"},{\"contractVersion\":\"v2\",\"assertionId\":\"a2\",\"valueType\":\"STRING\",\"operator\":\"EQUALS\",\"expected\":\"second\"}]");
        GlobalQualityGate.evaluate(policy,frozen,json.readTree("[{\"assertionId\":\"a2\",\"actual\":\"second\"},{\"assertionId\":\"a1\",\"actual\":\"first\"}]"),List.of());
        for(String observed:List.of("[{\"assertionId\":\"a1\",\"actual\":\"first\"},{\"assertionId\":\"a1\",\"actual\":\"second\"}]","[{\"assertionId\":\"unknown\",\"actual\":\"first\"},{\"assertionId\":\"a2\",\"actual\":\"second\"}]","[{\"assertionId\":\"a1\",\"actual\":\"first\",\"expected\":\"fake\"},{\"assertionId\":\"a2\",\"actual\":\"second\"}]"))assertThrows(MSException.class,()->GlobalQualityGate.evaluate(policy,frozen,json.readTree(observed),List.of()));
    }
}
