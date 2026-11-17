package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class BugImageValidatorTests {
    static byte[] image(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_BYTE_GRAY),format,output));
        return output.toByteArray();
    }
    void fails(String reason, byte[] bytes) {
        assertEquals("BUG_IMAGE_" + reason, assertThrows(MSException.class, () -> BugImageValidator.validate(bytes)).getMessage());
    }
    @Test void supportsPngJpegAndRawByteBoundariesForEachFormat() throws Exception {
        for (String format : new String[]{"png","jpeg"}) {
            byte[] valid=image(format,2,2); String mime="png".equals(format)?"image/png":"image/jpeg";
            assertEquals(mime,BugImageValidator.validate(valid));
            assertEquals(mime,BugImageValidator.validate(Arrays.copyOf(valid,BugImageValidator.MAX_BYTES)));
            fails("SIZE_LIMIT",Arrays.copyOf(valid,BugImageValidator.MAX_BYTES+1));
        }
        fails("EMPTY_FILE",new byte[0]);
    }
    @Test void supportsExactlyTwentyMillionPixelsForEachFormat() throws Exception {
        for (String format : new String[]{"png","jpeg"})
            assertNotNull(BugImageValidator.validate(image(format,5000,4000)));
    }
    @Test void rejectsPngAtExactlyOnePixelOverLimitBeforeDecoding() throws Exception {
        byte[] png=image("png",2,2);
        ByteBuffer.wrap(png,16,4).putInt(20_000_001); ByteBuffer.wrap(png,20,4).putInt(1);
        var crc=new java.util.zip.CRC32();crc.update(png,12,17);ByteBuffer.wrap(png,29,4).putInt((int)crc.getValue());
        fails("PIXEL_LIMIT",png);
    }
    @Test void rejectsJpegAtTheNearestRepresentablePixelCountAboveLimit() throws Exception {
        // JPEG dimensions are unsigned 16-bit. 20,000,001 has no factor pair <= 65535;
        // 20,000,002 = 2 * 10,000,001 also has none. Find the nearest actual JPEG rectangle.
        int width=0,height=0;
        for(int pixels=20_000_001;width==0;pixels++)for(int w=1;w<=65535;w++)
            if(pixels%w==0&&pixels/w<=65535){width=w;height=pixels/w;break;}
        fails("PIXEL_LIMIT",image("jpeg",width,height));
    }
    @Test void supportsTransparentGrayscaleAndProgressiveEncodings() throws Exception {
        var transparent=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(3,2,BufferedImage.TYPE_INT_ARGB),"png",transparent);
        assertEquals("image/png",BugImageValidator.validate(transparent.toByteArray()));
        assertEquals("image/png",BugImageValidator.validate(image("png",2,2)));
        var bytes=new ByteArrayOutputStream();var writer=ImageIO.getImageWritersByFormatName("jpeg").next();
        try(var output=new MemoryCacheImageOutputStream(bytes)){
            writer.setOutput(output);var params=writer.getDefaultWriteParam();params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null,new IIOImage(new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB),null,null),params);
        }finally{writer.dispose();}
        assertEquals("image/jpeg",BugImageValidator.validate(bytes.toByteArray()));
    }
    @Test void rejectsUnsupportedCorruptAndAnimationContent() throws Exception {
        fails("UNSUPPORTED_FORMAT",image("gif",2,2)); fails("UNSUPPORTED_FORMAT","RIFFxxxxWEBP".getBytes());
        byte[] png=image("png",2,2); fails("CORRUPT_IMAGE",Arrays.copyOf(png,40));
        byte[] broken=png.clone();broken[40]^=1;fails("CORRUPT_IMAGE",broken);
        byte[] apng=new byte[png.length+20];System.arraycopy(png,0,apng,0,33);
        ByteBuffer.wrap(apng,33,4).putInt(8);System.arraycopy("acTL".getBytes(),0,apng,37,4);
        System.arraycopy(png,33,apng,53,png.length-33);fails("UNSUPPORTED_FORMAT",apng);
    }
    @Test void onlyTemporaryStorageErrorsAreRetryable() {
        var mapper=new AgentSafeErrorMapper();
        for(String reason:new String[]{"REFERENCE_EXPIRED","EMPTY_FILE","SIZE_LIMIT","PIXEL_LIMIT","CORRUPT_IMAGE","UNSUPPORTED_FORMAT","INVALID_ARGUMENT"})
            assertEquals(false,mapper.toApiError(new MSException("BUG_IMAGE_"+reason),null).getDetails().get("retryable"));
        assertEquals(true,mapper.toApiError(new MSException("BUG_IMAGE_STORAGE_UNAVAILABLE"),null).getDetails().get("retryable"));
    }
}
