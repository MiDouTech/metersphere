package io.metersphere.agent.quality;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** v2 uses explicit types. Decimal and integer wire values are strings, never floating point. */
public final class TypedAssertionComparator {
    private TypedAssertionComparator() {}
    public static boolean compare(JsonNode rule,JsonNode actual){
        if(actual==null || actual.isNull())throw invalid();
        String type=rule.path("valueType").asText();String operator=rule.path("operator").asText();
        if(!Set.of("STRING","BOOLEAN","INTEGER","DECIMAL","DATE").contains(type) || !Set.of("EQUALS","IN_RANGE").contains(operator))throw invalid();
        JsonNode expected=rule.get("expected");if(expected==null || !expected.isTextual())throw invalid();
        if("DECIMAL".equals(type) || "INTEGER".equals(type)){
            BigDecimal value=number(actual,rule,type);
            if("EQUALS".equals(operator)) {
                BigDecimal target=number(expected,rule,type);
                BigDecimal tolerance=rule.hasNonNull("tolerance")?number(rule.get("tolerance"),rule,type):BigDecimal.ZERO;
                if(tolerance.signum()<0)throw invalid();return value.subtract(target).abs().compareTo(tolerance)<=0;
            }
            try {
                JsonNode range=new ObjectMapper().readTree(expected.textValue());
                if(!range.isArray() || range.size()!=2)throw invalid();
                BigDecimal low=number(range.get(0),rule,type),high=number(range.get(1),rule,type);
                if(low.compareTo(high)>0)throw invalid();
                return (rule.path("lowerInclusive").asBoolean(true)?value.compareTo(low)>=0:value.compareTo(low)>0)
                        && (rule.path("upperInclusive").asBoolean(true)?value.compareTo(high)<=0:value.compareTo(high)<0);
            } catch(MSException e){throw e;}catch(Exception e){throw invalid();}
        }
        if(!"EQUALS".equals(operator))throw invalid();
        if("BOOLEAN".equals(type)){
            if(!actual.isBoolean() || !Set.of("true","false").contains(expected.textValue()))throw invalid();
            return actual.booleanValue()==Boolean.parseBoolean(expected.textValue());
        }
        if(!actual.isTextual())throw invalid();
        if("DATE".equals(type)){
            try {
                if(!actual.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || !expected.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();
                return LocalDate.parse(actual.textValue()).equals(LocalDate.parse(expected.textValue()));
            }catch(Exception e){throw invalid();}
        }
        return actual.textValue().equals(expected.textValue());
    }
    private static BigDecimal number(JsonNode node,JsonNode rule,String type){
        if(node==null || !node.isTextual() || !node.textValue().matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))throw invalid();
        int precision=rule.path("precision").asInt(38),scale="INTEGER".equals(type)?0:rule.path("scale").asInt(-1);
        if(precision<1 || precision>38 || scale<0 || scale>18 || scale>precision)throw invalid();
        try {BigDecimal value=new BigDecimal(node.textValue());if(value.precision()>precision || value.scale()>scale)throw invalid();return value;}
        catch(NumberFormatException e){throw invalid();}
    }
    private static MSException invalid(){return new MSException("QUALITY_ASSERTION_INVALID");}
}
