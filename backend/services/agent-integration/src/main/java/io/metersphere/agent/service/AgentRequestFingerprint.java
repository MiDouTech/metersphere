package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import java.util.TreeSet;

/** Canonical object ordering; array order and primitive types retain their business meaning. */
final class AgentRequestFingerprint {
    private static final ObjectMapper JSON=new ObjectMapper();
    private AgentRequestFingerprint() { }
    static String hash(Object arguments) {
        try {
            JsonNode node=JSON.valueToTree(arguments);
            if(node.isObject())((com.fasterxml.jackson.databind.node.ObjectNode)node).remove("requestId");
            return DigestUtils.sha256Hex(JSON.writeValueAsString(sorted(node)));
        } catch(Exception error){throw new MSException("AGENT_SERIALIZATION_ERROR");}
    }
    private static JsonNode sorted(JsonNode node) {
        if(node.isObject()) {
            var result=JSON.createObjectNode();var names=new TreeSet<String>();node.fieldNames().forEachRemaining(names::add);
            for(String name:names)result.set(name,sorted(node.get(name)));return result;
        }
        if(node.isArray()){var result=JSON.createArrayNode();node.forEach(value->result.add(sorted(value)));return result;}
        return node;
    }
}
