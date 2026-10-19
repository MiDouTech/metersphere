package io.metersphere.agent.service;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** Explicit source adapters: never silently fall back from a published snapshot to a working copy. */
@Component
public class AgentCaseContentReader {
    public static final String CHECKER_VERSION = "case-content-v2";
    @Value("${agent.case-content.max-bytes:8388608}") private int maxBytes = 8 * 1024 * 1024;
    @Value("${agent.case-content.decode-timeout-ms:2000}") private long timeoutMs = 2000;
    private final ObjectMapper json = new ObjectMapper();
    public AgentCaseContentReader() {
        json.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        json.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(8 * 1024 * 1024).build());
    }
    public static class ContentException extends MSException {
        private final String field;
        private final Integer stepIndex;
        public ContentException(String code, String field, Integer stepIndex) { super(code); this.field=field; this.stepIndex=stepIndex; }
        public String field() { return field; }
        public Integer stepIndex() { return stepIndex; }
    }
    private ContentException error(String code, String field, Integer index) { return new ContentException(code,field,index); }
    public String decodeRaw(byte[] raw) {
        if(raw==null) return null;
        if(raw.length>maxBytes) throw error("CASE_CONTENT_LIMIT_EXCEEDED","",null);
        byte[] bytes=raw;
        if(raw.length>=2 && raw[0]=='P' && raw[1]=='K') {
            long deadline=System.nanoTime()+timeoutMs*1_000_000;
            try(var directory=new org.apache.commons.compress.archivers.zip.ZipFile(
                    new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(raw))) {
                var entries=directory.getEntries();
                if(!entries.hasMoreElements())throw error("CASE_CONTENT_DECODE_FAILED","",null);
                var entry=entries.nextElement();
                if(!"zip".equals(entry.getName()) || entry.isDirectory() || entries.hasMoreElements())throw error("CASE_CONTENT_DECODE_FAILED","",null);
                if(entry.getSize()>maxBytes)throw error("CASE_CONTENT_LIMIT_EXCEEDED","",null);
            } catch(ContentException e){throw e;}
            catch(IOException e){throw error("CASE_CONTENT_DECODE_FAILED","",null);}
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw));var out=new ByteArrayOutputStream()) {
                ZipEntry entry=zip.getNextEntry();
                if(entry==null || entry.isDirectory() || !"zip".equals(entry.getName())) throw error("CASE_CONTENT_DECODE_FAILED","",null);
                byte[] buffer=new byte[4096]; int size;
                while((size=zip.read(buffer))!=-1) {
                    if(out.size()+size>maxBytes || System.nanoTime()>deadline || Thread.currentThread().isInterrupted())
                        throw error("CASE_CONTENT_LIMIT_EXCEEDED","",null);
                    out.write(buffer,0,size);
                }
                zip.closeEntry();
                if(zip.getNextEntry()!=null) throw error("CASE_CONTENT_DECODE_FAILED","",null);
                bytes=out.toByteArray();
            } catch(ContentException e) { throw e; }
            catch(IOException e) { throw error("CASE_CONTENT_DECODE_FAILED","",null); }
        }
        return decodeUtf8(bytes);
    }
    public String decodeUtf8(byte[] bytes) {
        if(bytes==null) return null;
        if(bytes.length>maxBytes) throw error("CASE_CONTENT_LIMIT_EXCEEDED","",null);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException e) { throw error("CASE_CONTENT_DECODE_FAILED","",null); }
    }
    public JsonNode parse(String value) {
        if(value==null) throw error("CASE_CONTENT_MISSING","/content",null);
        if(value.getBytes(StandardCharsets.UTF_8).length>maxBytes) throw error("CASE_CONTENT_LIMIT_EXCEEDED","/content",null);
        try { JsonNode node=json.readTree(value); if(node!=null && !node.isNull()) return node; }
        catch(IOException e) { throw error("CASE_CONTENT_FORMAT_INVALID","/content",null); }
        throw error("CASE_CONTENT_FORMAT_INVALID","/content",null);
    }
    public AgentCaseDTO published(TestAssetVersionDTO version) {
        if(version==null) throw error("CASE_VERSION_NOT_PUBLISHED","/version",null);
        if(version.getContentSnapshot()==null || !DigestUtils.sha256Hex(version.getContentSnapshot()).equals(version.getContentHash()))
            throw error("CASE_CONTENT_HASH_INVALID","/content",null);
        JsonNode root=parse(version.getContentSnapshot());
        JsonNode content=root.has("content")?root.get("content"):root;
        if(content==null || !content.isObject()) throw error("CASE_CONTENT_FORMAT_INVALID","/content",null);
        AgentCaseDTO result=new AgentCaseDTO();
        result.setName(root.path("name").asText(content.path("name").asText()));
        result.setCaseId(content.path("id").asText(content.path("caseId").asText()));
        String type=content.path("editType").asText(content.path("caseEditType").asText());
        if(!Set.of("STEP","TEXT").contains(type)) throw error("CASE_CONTENT_FORMAT_INVALID","/caseEditType",null);
        result.setCaseEditType(type);
        result.setPrerequisite(text(content.get("prerequisite")));
        if(content.hasNonNull("contentSchemaVersion") && !"case-source.v1".equals(content.path("contentSchemaVersion").asText()))
            throw error("CASE_CONTENT_FORMAT_INVALID","/contentSchemaVersion",null);
        JsonNode steps=content.get("steps");
        if("TEXT".equals(type) && content.has("textDescription")) {
            var step=new AgentCaseStepDTO();step.setNum(1);
            step.setDesc(text(content.get("textDescription")));step.setExpected(text(content.get("expectedResult")));
            result.setSteps(List.of(step));
        } else {
            if(steps!=null && steps.isTextual()) steps=parse(steps.textValue());
            if(steps==null || steps.isNull()) throw error("CASE_CONTENT_MISSING","/steps",null);
            if(!steps.isArray()) throw error("CASE_CONTENT_FORMAT_INVALID","/steps",null);
            List<AgentCaseStepDTO> list=new ArrayList<>();
            for(JsonNode item:steps) {
                if(!item.isObject()) throw error("CASE_CONTENT_FORMAT_INVALID","/steps",list.size()+1);
                var step=new AgentCaseStepDTO();step.setId(text(item.get("id")));
                if(item.hasNonNull("num") && (!item.get("num").isIntegralNumber() || !item.get("num").canConvertToInt()))
                    throw error("CASE_CONTENT_FORMAT_INVALID","/steps/"+list.size()+"/num",list.size()+1);
                step.setNum(item.hasNonNull("num")?item.get("num").intValue():list.size()+1);
                step.setDesc(text(item.get("desc")));step.setExpected(text(item.has("expected")?item.get("expected"):item.get("result")));
                list.add(step);
            }
            result.setSteps(list);
        }
        validate(result);
        return result;
    }
    private String text(JsonNode node) {
        if(node==null || node.isNull()) return null;
        if(!node.isTextual()) throw error("CASE_CONTENT_FORMAT_INVALID","/content",null);
        return node.textValue();
    }
    public void validate(AgentCaseDTO content) {
        if(content.getSteps()==null || content.getSteps().isEmpty()) throw error("CASE_CONTENT_MISSING","/steps",null);
        Set<String> ids=new HashSet<>(); Set<Integer> positions=new HashSet<>();
        for(int i=0;i<content.getSteps().size();i++) {
            var step=content.getSteps().get(i);
            if(step.getId()!=null && !step.getId().isBlank() && !ids.add(step.getId())) throw error("CASE_CONTENT_FORMAT_INVALID","/steps/"+i+"/id",i+1);
            if(step.getNum()!=null && (step.getNum()<0 || !positions.add(step.getNum()))) throw error("CASE_CONTENT_FORMAT_INVALID","/steps/"+i+"/num",i+1);
            if(!meaningful(step.getDesc())) throw error("CASE_CONTENT_MISSING","/steps/"+i+"/desc",i+1);
            if(!meaningful(step.getExpected())) throw error("CASE_CONTENT_MISSING","/steps/"+i+"/expected",i+1);
        }
    }
    private boolean meaningful(String value) {
        if(value==null || value.isBlank()) return false;
        boolean[] found={false}; int[] ignored={0};
        try {
            new ParserDelegator().parse(new StringReader(value),new HTMLEditorKit.ParserCallback(){
                public void handleStartTag(HTML.Tag tag,MutableAttributeSet attrs,int pos) { if(tag==HTML.Tag.SCRIPT || tag==HTML.Tag.STYLE) ignored[0]++; }
                public void handleEndTag(HTML.Tag tag,int pos) { if(tag==HTML.Tag.SCRIPT || tag==HTML.Tag.STYLE) ignored[0]=Math.max(0,ignored[0]-1); }
                public void handleText(char[] data,int pos) { if(ignored[0]==0 && !new String(data).replace('\u00a0',' ').replace("\u200b","").isBlank()) found[0]=true; }
                public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet attrs,int pos) {
                    if(tag==HTML.Tag.IMG && attrs.getAttribute(HTML.Attribute.SRC)!=null && !attrs.getAttribute(HTML.Attribute.SRC).toString().isBlank()) found[0]=true;
                }
            },true);
        } catch(IOException e) { throw error("CASE_CONTENT_FORMAT_INVALID","/content",null); }
        return found[0];
    }
}
