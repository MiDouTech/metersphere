package io.metersphere.sdk.util;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class FunctionalCaseBlobCodecTests {
    private static byte[] text(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static byte[] zip(byte[] bytes) { return (byte[]) CompressUtils.zip(bytes); }
    @Test void plainAndSingleLayerRoundTrip() {
        byte[] original=text("[]");
        assertArrayEquals(original,(byte[])FunctionalCaseBlobCodec.unpack(original));
        assertArrayEquals(original,(byte[])FunctionalCaseBlobCodec.unpack(FunctionalCaseBlobCodec.pack(original)));
        assertArrayEquals(text("PK ordinary text �"),(byte[])FunctionalCaseBlobCodec.unpack(text("PK ordinary text �")));
        assertNull(FunctionalCaseBlobCodec.unpack(null));
    }
    @Test void nestedLayersBoundedAndWritesRejectStorageBytes() {
        byte[] one=zip(text("[]")),two=zip(one),three=zip(two);
        assertArrayEquals(text("[]"),(byte[])FunctionalCaseBlobCodec.unpack(three));
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(zip(three)));
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.pack(one));
    }
    @Test void malformedUtf8AndZipRejected() {
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(new byte[]{(byte)0xff}));
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(new byte[]{80,75,3,4,0}));
        byte[] broken=zip(text("[]"));broken[32]^=127;
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(broken));
    }
    @Test void multipleEntriesRejected() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var out=new ZipOutputStream(bytes)) {
            for(String name:new String[]{"zip","extra"}) {out.putNextEntry(new ZipEntry(name));out.write(text("[]"));out.closeEntry();}
        }
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(bytes.toByteArray()));
    }
    @Test void expansionBounded() {
        byte[] huge=new byte[FunctionalCaseBlobCodec.MAX_BYTES+1];
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(zip(huge)));
        assertThrows(MSException.class,()->FunctionalCaseBlobCodec.pack(huge));
    }
    @Test void realHistoricalCorruptionIsNotReportedAsEmptySuccess() throws Exception {
        try(var input=getClass().getResourceAsStream("/functional-case-corrupt-steps.hex")) {
            assertNotNull(input);
            byte[] bytes=java.util.HexFormat.of().parseHex(new String(input.readAllBytes(),StandardCharsets.US_ASCII).trim());
            assertEquals(370,bytes.length);
            assertThrows(MSException.class,()->FunctionalCaseBlobCodec.unpack(bytes));
        }
    }
}
