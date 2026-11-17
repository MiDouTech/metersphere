package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Strict, versioned input utilities. Untrusted values are never used in SQL identifiers or diagnostics. */
final class AgentProtocolData {
    static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private AgentProtocolData() {}
    static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String))) fail("AGENT_VALIDATION_ERROR");
        return new LinkedHashMap<>((Map<String,Object>) value);
    }
    static List<Map<String,Object>> objects(Object value) {
        if (!(value instanceof List<?>)) fail("AGENT_VALIDATION_ERROR");
        return ((List<?>)value).stream().map(AgentProtocolData::object).toList();
    }
    static List<String> strings(Object value) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) fail("AGENT_VALIDATION_ERROR");
        return List.copyOf((List<String>)value);
    }
    static String text(Map<String,Object> args,String key) {
        if (!(args.get(key) instanceof String text) || text.isBlank()) fail("AGENT_VALIDATION_ERROR");
        return (String) args.get(key);
    }
    static String id(Map<String,Object> args,String key) {
        String text=text(args,key);
        if(!text.matches("[A-Za-z0-9_.:-]{1,128}")) fail("AGENT_VALIDATION_ERROR");
        return text;
    }
    static long number(Map<String,Object> args,String key) {
        Object value=args.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) fail("AGENT_VALIDATION_ERROR");
        long result=((Number)value).longValue(); if(result<0)fail("AGENT_VALIDATION_ERROR"); return result;
    }
    static void fields(Map<String,Object> args,String... allowed) {
        Set<String> keys=new HashSet<>(List.of(allowed));
        keys.addAll(List.of("protocolVersion","idempotencyKey","traceId","clientTime","transportTime"));
        if(!keys.containsAll(args.keySet()))fail("PROTOCOL_UNKNOWN_FIELD");
        if(args.containsKey("protocolVersion") && !"1.0".equals(args.get("protocolVersion")))fail("PROTOCOL_VERSION_UNSUPPORTED");
    }
    static String encode(Object value) {
        try{return JSON.writeValueAsString(value);}catch(Exception failure){throw new MSException("AGENT_SERIALIZATION_ERROR");}
    }
    static Map<String,Object> decode(String value) {
        try{return JSON.readValue(value,LinkedHashMap.class);}catch(Exception failure){throw new MSException("PROTOCOL_DATA_UNAVAILABLE");}
    }
    static String hash(Object value) {return AgentRequestFingerprint.hash(value);}
    static String requestHash(Map<String,Object> value) {
        var copy=new LinkedHashMap<>(value); copy.keySet().removeAll(Set.of("traceId","clientTime","transportTime","idempotencyKey"));
        return hash(copy);
    }
    static String limited(String value,long scalars,long bytes) {
        for(int index=0;index<value.length();index++){char c=value.charAt(index);if(Character.isHighSurrogate(c)){if(index+1>=value.length()||!Character.isLowSurrogate(value.charAt(++index)))fail("AGENT_VALIDATION_ERROR");}else if(Character.isLowSurrogate(c))fail("AGENT_VALIDATION_ERROR");}
        String normalized=Normalizer.normalize(value,Normalizer.Form.NFC);
        if(normalized.codePointCount(0,normalized.length())>scalars || normalized.getBytes(StandardCharsets.UTF_8).length>bytes)
            fail("PROTOCOL_SIZE_LIMIT");
        if(normalized.chars().anyMatch(c -> c==0))fail("AGENT_VALIDATION_ERROR");
        return normalized;
    }
    static List<Map<String,Object>> effectiveSteps(Map<String,Object> data){var result=new LinkedHashMap<String,Map<String,Object>>();for(var step:objects(data.getOrDefault("steps",List.of())))result.put(id(step,"stepId"),step);for(var step:objects(data.getOrDefault("correctedSteps",List.of())))result.put(id(step,"stepId"),step);return new ArrayList<>(result.values());}
    static String uuid(){return UUID.randomUUID().toString();}
    static void require(boolean condition,String code){if(!condition)fail(code);}
    static void fail(String code){throw new MSException(code);}
}
