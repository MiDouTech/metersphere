package io.metersphere.agent.service;

import io.metersphere.sdk.file.*;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Real MinIO SDK over controlled S3 HTTP semantics; not a deployed MinIO engine test. */
class BugImageMinioGenerationTests {
    record ObjectState(byte[] bytes,Map<String,String> metadata,String contentType) { }
    @Test void actualSdkNoSuchKeyRemainsAPermanentResourceFailureAtIndexAndReadTime() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{exchange.getResponseHeaders().set("x-amz-request-id","fixture");exchange.sendResponseHeaders(404,-1);exchange.close();});server.start();
        try {
            var repository=new MinioRepository();ReflectionTestUtils.setField(repository,"bucket","fixture-bucket");
            repository.init(MinioClient.builder().endpoint("http://127.0.0.1:"+server.getAddress().getPort()).region("us-east-1").build());
            var failure=assertThrows(io.minio.errors.ErrorResponseException.class,()->repository.objectRevision(new FileRequest("project/p/file-management","MINIO","missing.png")));
            assertEquals("NoSuchKey",failure.errorResponse().code());
            var fixture=new AgentBugImageServiceTests();fixture.setup();String bound=fixture.ref();
            org.mockito.Mockito.doThrow(failure).when(fixture.store).revision(org.mockito.ArgumentMatchers.any());
            var entry=fixture.detail().getImages().getFirst();assertEquals(false,entry.get("readable"));assertEquals("RESOURCE_UNAVAILABLE",entry.get("reason"));assertEquals(false,entry.get("retryable"));
            fixture.fails("RESOURCE_UNAVAILABLE",()->fixture.service.read("p","b",bound));
        }finally{server.stop(0);}
    }
    @Test void unversionedSameBytePutsAndCopiesCarryDistinctGenerationsWithoutLosingCopyMetadata() throws Exception {
        byte[] png=BugImageValidatorTests.image("png",2,2);
        var object=new AtomicReference<>(new ObjectState(png,Map.of(),"image/png"));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            try {
                var state=object.get();String etag=org.apache.commons.codec.digest.DigestUtils.md5Hex(state.bytes());
                if(exchange.getRequestMethod().equals("HEAD")){
                    var headers=exchange.getResponseHeaders();headers.set("Last-Modified","Thu, 05 Nov 2026 08:00:00 GMT");
                    headers.set("ETag","\""+etag+"\"");headers.set("Content-Length",Integer.toString(state.bytes().length));headers.set("Content-Type",state.contentType());
                    state.metadata().forEach((key,value)->headers.set("x-amz-meta-"+key,value));exchange.sendResponseHeaders(200,-1);
                } else if(exchange.getRequestMethod().equals("PUT")){
                    var metadata=new HashMap<String,String>();exchange.getRequestHeaders().forEach((key,values)->{
                        if(key.toLowerCase(Locale.ROOT).startsWith("x-amz-meta-"))metadata.put(key.substring(11).toLowerCase(Locale.ROOT),values.getFirst());
                    });
                    boolean copy=exchange.getRequestHeaders().containsKey("x-amz-copy-source");
                    byte[] bytes=copy?state.bytes():exchange.getRequestBody().readAllBytes();
                    String contentType=Objects.toString(exchange.getRequestHeaders().getFirst("Content-Type"),"application/octet-stream");
                    object.set(new ObjectState(bytes,Map.copyOf(metadata),contentType));
                    exchange.getResponseHeaders().set("ETag","\""+org.apache.commons.codec.digest.DigestUtils.md5Hex(bytes)+"\"");
                    byte[] response=copy?("<CopyObjectResult><ETag>\""+etag+"\"</ETag><LastModified>2026-11-05T08:00:00.000Z</LastModified></CopyObjectResult>").getBytes(StandardCharsets.UTF_8):new byte[0];
                    exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
                } else exchange.sendResponseHeaders(405,-1);
            }finally{exchange.close();}
        });
        server.start();
        try {
            var client=MinioClient.builder().endpoint("http://127.0.0.1:"+server.getAddress().getPort()).region("us-east-1").build();
            var repository=new MinioRepository();ReflectionTestUtils.setField(repository,"bucket","fixture-bucket");repository.init(client);
            var request=new FileRequest("project/p/file-management","MINIO","proof.png");String previous=repository.objectRevision(request);
            for(int mode=0;mode<3;mode++){
                if(mode==0)repository.saveFile(png,request);else if(mode==1)repository.saveFile(new ByteArrayInputStream(png),request);
                else repository.saveFile(new MockMultipartFile("file","proof.png","image/png",png),request);
                String next=repository.objectRevision(request);assertNotEquals(previous,next);previous=next;
                assertArrayEquals(png,object.get().bytes());assertNotNull(object.get().metadata().get("ms-object-generation"));
            }
            var metadata=new HashMap<>(object.get().metadata());metadata.put("legacy-note","preserve-me");object.set(new ObjectState(png,metadata,"image/png"));
            var copy=new FileCopyRequest();copy.setStorage("MINIO");copy.setFolder("project/p/file-management");copy.setFileName("proof.png");copy.setCopyFolder("project/p/file-management");copy.setCopyfileName("proof.png");
            repository.copyFile(copy);assertNotEquals(previous,repository.objectRevision(request));
            assertEquals("preserve-me",object.get().metadata().get("legacy-note"));assertEquals("image/png",object.get().contentType());assertArrayEquals(png,object.get().bytes());
        }finally{server.stop(0);}
    }
}
