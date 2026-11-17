package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Validate original bytes without transcoding, allocating pixels only after the dimension check. */
final class BugImageValidator {
    static final int MAX_BYTES = 5_242_880;
    static final long MAX_PIXELS = 20_000_000L;

    static String validate(byte[] bytes) {
        if (bytes.length == 0) throw failure("EMPTY_FILE");
        if (bytes.length > MAX_BYTES) throw failure("SIZE_LIMIT");
        boolean png = bytes.length >= 8 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL;
        boolean jpeg = bytes.length >= 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
        if (!png && !jpeg) throw failure("UNSUPPORTED_FORMAT");
        if (png) {
            // ImageIO's PNG reader ignores APNG animation chunks. Reject those explicitly.
            int offset = 8;
            boolean ended = false;
            while (offset <= bytes.length - 12) {
                long length = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).getInt());
                if (length > bytes.length - offset - 12L) throw failure("CORRUPT_IMAGE");
                String chunk = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
                if (chunk.equals("acTL") || chunk.equals("fcTL") || chunk.equals("fdAT")) throw failure("UNSUPPORTED_FORMAT");
                var crc = new java.util.zip.CRC32();
                crc.update(bytes, offset + 4, (int) length + 4);
                if (crc.getValue() != Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + 8 + (int) length, 4).getInt()))
                    throw failure("CORRUPT_IMAGE");
                offset += (int) length + 12;
                if (chunk.equals("IEND")) { ended = true; break; }
            }
            if (!ended) throw failure("CORRUPT_IMAGE");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw failure("CORRUPT_IMAGE");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw failure("CORRUPT_IMAGE");
                if ((long) width * height > MAX_PIXELS) throw failure("PIXEL_LIMIT");
                if (reader.getNumImages(true) != 1) throw failure("UNSUPPORTED_FORMAT");
                final boolean[] warning = {false};
                reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                var decoded = reader.read(0);
                if (warning[0] || decoded == null || decoded.getWidth() != width || decoded.getHeight() != height)
                    throw failure("CORRUPT_IMAGE");
                return png ? "image/png" : "image/jpeg";
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException error) {
            throw failure("CORRUPT_IMAGE");
        }
    }

    static MSException failure(String reason) { return new MSException("BUG_IMAGE_" + reason); }
}
