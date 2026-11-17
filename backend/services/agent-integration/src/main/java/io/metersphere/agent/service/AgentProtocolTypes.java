package io.metersphere.agent.service;

import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Explicit field contracts only: no expression evaluation or implicit type conversion. */
final class AgentProtocolTypes {
    private AgentProtocolTypes(){}
    static void definition(Map<String,Object> fields){definition(fields,0);}
    private static void definition(Map<String,Object> fields,int depth){require(depth<32,"PROTOCOL_TYPE_INVALID");
        for(var entry:fields.entrySet()){
            require(entry.getKey().matches("[A-Za-z][A-Za-z0-9_]*"),"PROTOCOL_VARIABLE_UNDECLARED");var spec=object(entry.getValue());
            fields(spec,"type","nullable","required","unit","enum","minimum","maximum","scope","properties","items");
            require(Set.of("string","number","integer","boolean","object","array").contains(text(spec,"type")),"PROTOCOL_TYPE_INVALID");
            if(spec.containsKey("properties")){require("object".equals(spec.get("type")),"PROTOCOL_TYPE_INVALID");definition(object(spec.get("properties")),depth+1);}
            if(spec.containsKey("items")){require("array".equals(spec.get("type")),"PROTOCOL_TYPE_INVALID");definition(Map.of("item",spec.get("items")),depth+1);}
            if(spec.containsKey("unit"))require(spec.get("unit") instanceof String,"PROTOCOL_TYPE_INVALID");
            if(spec.containsKey("enum"))require(spec.get("enum") instanceof List<?> list&&!list.isEmpty(),"PROTOCOL_TYPE_INVALID");
            for(String bound:List.of("minimum","maximum"))if(spec.containsKey(bound))require(Set.of("number","integer").contains(spec.get("type"))&&spec.get(bound) instanceof Number number&&Double.isFinite(number.doubleValue()),"PROTOCOL_TYPE_INVALID");
            if(spec.containsKey("minimum")&&spec.containsKey("maximum"))require(((Number)spec.get("minimum")).doubleValue()<=((Number)spec.get("maximum")).doubleValue(),"PROTOCOL_TYPE_INVALID");
            for(String flag:List.of("nullable","required"))if(spec.containsKey(flag))require(spec.get(flag) instanceof Boolean,"PROTOCOL_TYPE_INVALID");
            if(spec.containsKey("scope"))require(Set.of("CASE","EXECUTION").contains(spec.get("scope")),"PROTOCOL_TYPE_INVALID");
        }
    }
    static void values(Map<String,Object> declarations,Map<String,Object> values,boolean complete){
        definition(declarations);require(declarations.keySet().containsAll(values.keySet()),"PROTOCOL_VARIABLE_UNDECLARED");
        for(var entry:declarations.entrySet()){
            var spec=object(entry.getValue());boolean present=values.containsKey(entry.getKey());
            require(!complete||!Boolean.TRUE.equals(spec.getOrDefault("required",true))||present,"PROTOCOL_OUTPUT_INCOMPLETE");if(!present)continue;
            Object value=values.get(entry.getKey());if(value==null){require(Boolean.TRUE.equals(spec.get("nullable")),"PROTOCOL_TYPE_INVALID");continue;}
            boolean matches=switch(text(spec,"type")){
                case "string"->value instanceof String;case "number"->value instanceof Number&&Double.isFinite(((Number)value).doubleValue());
                case "integer"->value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long;
                case "boolean"->value instanceof Boolean;case "object"->value instanceof Map;case "array"->value instanceof List;default->false;
            };require(matches,"PROTOCOL_TYPE_INVALID");
            if("object".equals(spec.get("type"))&&spec.containsKey("properties"))values(object(spec.get("properties")),object(value),complete);
            if("array".equals(spec.get("type"))&&spec.containsKey("items"))for(Object item:(List<?>)value){var single=new LinkedHashMap<String,Object>();single.put("item",item);values(Map.of("item",spec.get("items")),single,complete);}
            if(spec.containsKey("enum"))require(spec.get("enum") instanceof List<?> list&&list.contains(value),"PROTOCOL_TYPE_INVALID");
            if(value instanceof Number number){if(spec.get("minimum") instanceof Number minimum)require(number.doubleValue()>=minimum.doubleValue(),"PROTOCOL_TYPE_INVALID");if(spec.get("maximum") instanceof Number maximum)require(number.doubleValue()<=maximum.doubleValue(),"PROTOCOL_TYPE_INVALID");}
        }
    }
    static Map<String,Object> leafFields(Map<String,Object> declarations){definition(declarations);var result=new LinkedHashMap<String,Object>();flatten(declarations,"",result,0);return result;}
    private static void flatten(Map<String,Object> fields,String prefix,Map<String,Object> result,int depth){require(depth<32,"PROTOCOL_TYPE_INVALID");for(var entry:fields.entrySet()){var spec=object(entry.getValue());String name=prefix+entry.getKey();if("object".equals(spec.get("type"))&&spec.containsKey("properties")){require(!Boolean.TRUE.equals(spec.get("nullable"))&&!Boolean.FALSE.equals(spec.get("required")),"PROTOCOL_NEW_INSTANCE_REQUIRED");flatten(object(spec.get("properties")),name+".",result,depth+1);}else result.put(name,spec);}}
    static boolean compatible(Map<String,Object> first,Map<String,Object> second){return canonical(first).equals(canonical(second));}
    private static Map<String,Object> canonical(Map<String,Object> value){var result=new LinkedHashMap<>(value);result.remove("scope");result.putIfAbsent("required",true);result.putIfAbsent("nullable",false);result.putIfAbsent("unit","");return result;}
}
