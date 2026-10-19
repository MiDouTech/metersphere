package io.metersphere.agent.service;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded facts; never sanitize or abbreviate values used by the quality evaluator. */
public final class AgentStepFacts {
    public static final String VERSION="facts-v1";
    private static final ObjectMapper JSON=new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    static {JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(65535).build());}
    private AgentStepFacts() {}
    public static String bounded(String value,int limit) {
        if(value!=null && value.getBytes(StandardCharsets.UTF_8).length>limit)throw new MSException("STEP_FACTS_LIMIT_EXCEEDED");
        return value;
    }
    public static String assertions(String value) {return assertions(value,false);}
    public static String assertions(String value,boolean v2) {
        if(value==null)return null;
        bounded(value,65535);
        try {
            JsonNode node=JSON.readTree(value);
            if(node==null || !node.isArray() || node.size()>256)throw new MSException("QUALITY_ASSERTION_INVALID");
            if(v2 && !node.isEmpty()){
                Map<String,JsonNode> items=new TreeMap<>();
                for(JsonNode item:node){String id=item.path("assertionId").asText();if(id.isBlank() || items.put(id,item)!=null)throw new MSException("QUALITY_ASSERTION_INVALID");}
                var reordered=JSON.createArrayNode();items.values().forEach(reordered::add);node=reordered;
            }
            String canonical=JSON.writeValueAsString(sorted(node));return bounded(canonical,65535);
        } catch(MSException e){throw e;}
        catch(Exception e){throw new MSException("QUALITY_ASSERTION_INVALID");}
    }
    private static JsonNode sorted(JsonNode node) {
        if(node.isObject()) {
            var out=JSON.createObjectNode();List<String> keys=new ArrayList<>();node.fieldNames().forEachRemaining(keys::add);Collections.sort(keys);
            keys.forEach(key->out.set(key,sorted(node.get(key))));return out;
        }
        if(node.isArray()){var out=JSON.createArrayNode();node.forEach(item->out.add(sorted(item)));return out;}
        return node;
    }
    public static String hash(Map<String,Object> values) {
        try{return DigestUtils.sha256Hex(JSON.writeValueAsString(sorted(JSON.valueToTree(values))));}
        catch(Exception e){throw new MSException("STEP_FACTS_INVALID");}
    }
}
