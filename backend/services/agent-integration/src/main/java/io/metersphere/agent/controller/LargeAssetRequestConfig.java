package io.metersphere.agent.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.service.DataAssetLimits;
import java.io.*;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Raises string capacity only for explicitly opted-in request bodies. */
@Configuration
public class LargeAssetRequestConfig implements WebMvcConfigurer {
  public static final int MAX_REQUEST_BYTES = DataAssetLimits.MAX_CONTENT_BYTES + 1024 * 1024;
  @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.addFirst(new Converter());
  }
  public static class BodyCapacityException extends IOException { public BodyCapacityException(){super("Request capacity exceeded");} }
  public static class BodyJsonConstraintException extends IOException { public BodyJsonConstraintException(){super("JSON constraint exceeded");} }
  public static class BodyJsonSyntaxException extends IOException { public BodyJsonSyntaxException(){super("Invalid JSON syntax");} }
  public static class BodyReadException extends IOException { public BodyReadException(){super("Request read failed");} }
  public static final class Converter extends AbstractHttpMessageConverter<LargeAssetRequest> {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxStringLength(DataAssetLimits.MAX_CONTENT_BYTES).build()).build());
    public Converter() { super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json")); }
    @Override protected boolean supports(Class<?> type) { return type == LargeAssetRequest.class; }
    @Override public boolean canWrite(Class<?> type, MediaType mediaType) { return false; }
    @Override protected LargeAssetRequest readInternal(Class<? extends LargeAssetRequest> type, HttpInputMessage input) throws IOException {
      if (input.getHeaders().getContentLength() > MAX_REQUEST_BYTES)
        throw new HttpMessageNotReadableException("请求内容过大，请减少数据量", new BodyCapacityException(), input);
      InputStream bounded = new FilterInputStream(input.getBody()) {
        long remaining = MAX_REQUEST_BYTES;
        @Override public int read() throws IOException {
          int value=super.read(); if(value!=-1 && --remaining<0) throw new BodyCapacityException(); return value;
        }
        @Override public int read(byte[] bytes,int offset,int length) throws IOException {
          int count=in.read(bytes,offset,(int)Math.min(length,remaining+1));
          if(count>0 && (remaining-=count)<0) throw new BodyCapacityException(); return count;
        }
      };
      try (var parser=mapper.getFactory().createParser(bounded)) {
        LargeAssetRequest value=mapper.readValue(parser, LargeAssetRequest.class);
        if(value==null || parser.nextToken()!=null) throw new BodyJsonSyntaxException();
        return value;
      } catch(IOException error) {
        // Retain only a fixed safe classification; raw Jackson errors can contain private fragments.
        boolean capacity=false, constraint=false;
        Throwable cursor=error;
        for(int i=0;i<12 && cursor!=null;i++,cursor=cursor.getCause()) {
          capacity |= cursor instanceof BodyCapacityException;
          constraint |= cursor instanceof com.fasterxml.jackson.core.exc.StreamConstraintsException;
        }
        IOException safe;
        if (capacity) safe=new BodyCapacityException();
        else if (constraint) safe=new BodyJsonConstraintException();
        else if (error instanceof com.fasterxml.jackson.core.JsonProcessingException || error instanceof BodyJsonSyntaxException) safe=new BodyJsonSyntaxException();
        else safe=new BodyReadException();
        throw new HttpMessageNotReadableException("请求内容无效或超过容量限制，请检查JSON格式并减少数据量", safe, input);
      }
    }
    @Override protected void writeInternal(LargeAssetRequest body,HttpOutputMessage output) { throw new UnsupportedOperationException(); }
  }
}
