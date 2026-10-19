package io.metersphere.agent.service;
import org.junit.jupiter.api.Test;
import io.metersphere.sdk.exception.MSException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class AgentStepFactsTests {
    @Test void retainsExactValuesAndCanonicalizesObjectKeysButNotArrayOrder(){
        assertEquals("[{\"actual\":\"token=literal\",\"b\":1}]",AgentStepFacts.assertions("[ {\"b\":1,\"actual\":\"token=literal\"} ]"));
        assertNotEquals(AgentStepFacts.hash(Map.of("facts",AgentStepFacts.assertions("[{\"actual\":1},{\"actual\":2}]"))),
                AgentStepFacts.hash(Map.of("facts",AgentStepFacts.assertions("[{\"actual\":2},{\"actual\":1}]"))));
    }
    @Test void rejectsInvalidJsonTrailingValuesAndByteOverflow(){
        for(String invalid:new String[]{"[]{}","{","null","{}","[NaN]"})assertThrows(MSException.class,()->AgentStepFacts.assertions(invalid));
        assertThrows(MSException.class,()->AgentStepFacts.assertions("[{\"actual\":\""+"中".repeat(22000)+"\"}]"));
        assertEquals(65535,AgentStepFacts.bounded("x".repeat(65535),65535).length());
    }
}
