package io.metersphere.agent.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import io.metersphere.agent.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataAssetHttpCapacityTests {
  private org.springframework.test.web.servlet.MockMvc mvc(DataAssetService service) {
    List<HttpMessageConverter<?>> converters=new ArrayList<>();
    converters.add(new MappingJackson2HttpMessageConverter());
    new LargeAssetRequestConfig().extendMessageConverters(converters);
    assertInstanceOf(LargeAssetRequestConfig.Converter.class,converters.getFirst());
    assertFalse(converters.getFirst().canRead(Map.class,MediaType.APPLICATION_JSON));
    return MockMvcBuilders.standaloneSetup(new DataAssetController(service,null,null,null,null))
      .setControllerAdvice(new DataAssetExceptionHandler())
      .setMessageConverters(converters.toArray(HttpMessageConverter[]::new)).build();
  }
  @Test void largeCreateAndUpdateReachServicesThroughRegisteredConverter() throws Exception {
    var service=mock(DataAssetService.class); var mvc=mvc(service);
    String text="x".repeat(30*1024*1024);
    String body="{\"content\":\""+text+"\",\"format\":\"TXT\"}";
    assertEquals(200,mvc.perform(post("/test-data-assets").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
    assertEquals(200,mvc.perform(put("/test-data-assets/a").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
    verify(service).save(eq(null),argThat(r->text.equals(r.get("content"))));
    verify(service).save(eq("a"),argThat(r->text.equals(r.get("content"))));
  }
  @Test void actualMcpRouteAcceptsThirtyMiBBase64AndReturnsSafeParseError() throws Exception {
    var service=mock(AgentMcpStreamableService.class);
    var controller=new AgentMcpStreamableController();
    org.springframework.test.util.ReflectionTestUtils.setField(controller,"agentMcpStreamableService",service);
    List<HttpMessageConverter<?>> converters=new ArrayList<>(List.of(new MappingJackson2HttpMessageConverter()));
    new LargeAssetRequestConfig().extendMessageConverters(converters);
    var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new McpBodyExceptionHandler())
      .setMessageConverters(converters.toArray(HttpMessageConverter[]::new)).build();
    String base64="A".repeat(41943040);
    assertEquals(200,mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
      .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"params\":{\"base64\":\""+base64+"\"}}")).andReturn().getResponse().getStatus());
    verify(service).handle(argThat(r->base64.equals(((Map<?,?>)r.get("params")).get("base64"))),isNull());
    var invalid=mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{privateSecret INVALID")).andReturn().getResponse();
    assertEquals(400,invalid.getStatus());assertTrue(invalid.getContentAsString().contains("-32700"));assertFalse(invalid.getContentAsString().contains("privateSecret"));
  }
  @Test void malformedMcpLogsOnlySafeCorrelatedDiagnostics() {
    var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(McpBodyExceptionHandler.class);
    var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();logger.addAppender(appender);
    try {
      var input=new org.springframework.mock.http.MockHttpInputMessage(new byte[0]);
      var response=new McpBodyExceptionHandler().invalidBody(new org.springframework.http.converter.HttpMessageNotReadableException("invalidSecret",new IllegalArgumentException("invalidSecret"),input));
      var error=(Map<?,?>)response.getBody().get("error");
      String trace=((Map<?,?>)error.get("data")).get("traceId").toString();
      assertDoesNotThrow(()->java.util.UUID.fromString(trace));
      assertEquals(-32700,error.get("code"));
      assertFalse(response.getBody().toString().contains("invalidSecret"));
      assertEquals(1,appender.list.size());
      var event=appender.list.getFirst();String log=event.getFormattedMessage();
      assertTrue(log.contains("MCP_REQUEST_BODY_FAILURE"));assertTrue(log.contains("stage=decode"));
      assertTrue(log.contains("category=MCP_PARSE_ERROR"));assertTrue(log.contains("traceId="+trace));
      assertFalse(log.contains("invalidSecret"));assertNull(event.getThrowableProxy());
    } finally {logger.detachAppender(appender);appender.stop();}
  }
  @Test void unknownLengthStreamEnforcesTotalBudgetWhileScanningTrailingWhitespace() {
    var readCount=new java.util.concurrent.atomic.AtomicLong();
    java.io.InputStream stream=new java.io.InputStream() {
      @Override public int read() {
        long index=readCount.getAndIncrement();
        return index==0?'{':index==1?'}':' ';
      }
      @Override public int read(byte[] bytes,int offset,int length) {
        for(int i=0;i<length;i++)bytes[offset+i]=(byte)read();
        return length;
      }
    };
    var input=new org.springframework.http.HttpInputMessage() {
      private final org.springframework.http.HttpHeaders headers=new org.springframework.http.HttpHeaders();
      @Override public org.springframework.http.HttpHeaders getHeaders() {return headers;}
      @Override public java.io.InputStream getBody() {return stream;}
    };
    assertEquals(-1,input.getHeaders().getContentLength());
    var error=assertThrows(org.springframework.http.converter.HttpMessageNotReadableException.class,
      ()->new LargeAssetRequestConfig.Converter().read(LargeAssetRequest.class,input));
    assertEquals((long)LargeAssetRequestConfig.MAX_REQUEST_BYTES+1,readCount.get());
    assertInstanceOf(LargeAssetRequestConfig.BodyCapacityException.class,error.getCause());
    assertNull(error.getCause().getCause());
    assertEquals("Request capacity exceeded",error.getCause().getMessage());
    assertEquals("请求内容无效或超过容量限制，请检查JSON格式并减少数据量",error.getMessage());
  }
  @Test void malformedTrailingAndOversizedBodiesNeverReachService() throws Exception {
    var service=mock(DataAssetService.class);var mvc=mvc(service);
    for(String body:List.of("{privateSecret INVALID","{} {}","{\"content\":\""+"x".repeat(LargeAssetRequestConfig.MAX_REQUEST_BYTES)+"\"}")) {
      var response=mvc.perform(post("/test-data-assets").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
      assertEquals(400,response.getStatus());assertFalse(response.getContentAsString().contains("privateSecret"));
    }
    verifyNoInteractions(service);
  }
}
