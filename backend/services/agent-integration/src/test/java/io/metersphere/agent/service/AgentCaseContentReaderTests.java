package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentCaseContentReaderTests {
    private final AgentCaseContentReader reader=new AgentCaseContentReader();
    private byte[] zip(String value,String... names) throws Exception {
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out)) {for(String name:names){zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        return out.toByteArray();
    }
    private TestAssetVersionDTO version(String content){
        var v=new TestAssetVersionDTO();v.setContentSnapshot(content);v.setContentHash(DigestUtils.sha256Hex(content));return v;
    }
    @Test void rawZipAndLegacyUtf8AreDecodedWithoutMutation() throws Exception {
        String source="操作与预期";byte[] raw=zip(source,"zip");byte[] original=raw.clone();
        assertEquals(source,reader.decodeRaw(raw));assertArrayEquals(original,raw);
        assertEquals(source,reader.decodeRaw(source.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsInvalidEncodingAndUnexpectedEntries() throws Exception {
        assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(new byte[]{(byte)0xc3,0x28}));
        assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(zip("valid","zip","extra")));
        assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(zip("valid","../zip")));
        assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(new byte[]{'P','K',0,0}));
    }
    @Test void rejectsTruncatedArchiveAndCorruptedPayload() throws Exception {
        byte[] original=zip("nonempty content","zip");
        assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(java.util.Arrays.copyOf(original,original.length-22)));
        byte[] corrupt=original.clone();corrupt[36]^=0x55;
        assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(corrupt));
    }
    @Test void rejectsExpansionBeyondLimit() throws Exception {
        byte[] raw=zip("x".repeat(8*1024*1024+1),"zip");
        assertEquals("CASE_CONTENT_LIMIT_EXCEEDED",assertThrows(AgentCaseContentReader.ContentException.class,()->reader.decodeRaw(raw)).getMessage());
    }
    @Test void readsFrozenStepAndTextFormats() {
        var step=reader.published(version("""
                {"content":{"editType":"STEP","steps":[{"num":1,"desc":"点击","result":"显示结果"}]}}
                """));
        assertEquals("显示结果",step.getSteps().getFirst().getExpected());
        assertEquals(1,reader.published(version("""
                {"content":{"editType":"TEXT","textDescription":"操作","expectedResult":"预期"}}
                """)).getSteps().size());
    }
    @Test void rejectsMissingFieldsEmptyMarkupAndHashMismatch() {
        for(String value:new String[]{"[]","[{\"desc\":\"<p>&nbsp;</p>\",\"result\":\"yes\"}]","[{\"desc\":\"click\"}]"}){
            var e=assertThrows(AgentCaseContentReader.ContentException.class,()->reader.published(version("{\"editType\":\"STEP\",\"steps\":"+value+"}")));
            assertEquals("CASE_CONTENT_MISSING",e.getMessage());
        }
        var v=version("{}");v.setContentHash("invalid");
        assertEquals("CASE_CONTENT_HASH_INVALID",assertThrows(AgentCaseContentReader.ContentException.class,()->reader.published(v)).getMessage());
    }
}
