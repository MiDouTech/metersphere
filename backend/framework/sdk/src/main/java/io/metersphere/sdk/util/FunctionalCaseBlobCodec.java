package io.metersphere.sdk.util;

import io.metersphere.sdk.exception.MSException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.zip.*;

/** Logical UTF-8 in memory, one ZIP on disk; legacy layers are bounded. */
public final class FunctionalCaseBlobCodec {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_LAYERS = 3;
    private FunctionalCaseBlobCodec() { }
    public static Object pack(Object value) {
        if (!(value instanceof byte[] bytes)) return value;
        if (isZip(bytes)) throw invalid();
        validateText(bytes);
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            ZipEntry entry = new ZipEntry("zip");
            entry.setTime(0); // Canonical bytes support repair retries and raw-hash compare-and-set.
            zip.putNextEntry(entry);
            zip.write(bytes);zip.closeEntry();zip.finish();
            return output.toByteArray();
        } catch (IOException failure) { throw invalid(); }
    }
    public static Object unpack(Object value) {
        if (!(value instanceof byte[] bytes)) return value;
        byte[] result = bytes;
        int total = 0;
        for (int layer = 0; isZip(result); layer++) {
            if (layer >= MAX_LAYERS) throw invalid();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(result));
                 var output = new ByteArrayOutputStream()) {
                ZipEntry entry = zip.getNextEntry();
                if (entry == null || entry.isDirectory() || !"zip".equals(entry.getName())) throw invalid();
                byte[] buffer = new byte[4096];int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw invalid();
                    output.write(buffer, 0, count);
                }
                zip.closeEntry();
                if (zip.getNextEntry() != null) throw invalid();
                result = output.toByteArray();
            } catch (IOException failure) { throw invalid(); }
        }
        validateText(result);
        return result.clone();
    }
    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
            && ((bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6)
                || (bytes[2] == 7 && bytes[3] == 8));
    }
    private static void validateText(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw invalid();
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException failure) { throw invalid(); }
    }
    public static final class BodyFormatException extends MSException {
        public BodyFormatException() { super("用例正文格式异常或超过限制，原始内容已保留，请联系管理员核对数据"); }
    }
    private static MSException invalid() { return new BodyFormatException(); }
}
