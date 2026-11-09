package io.metersphere.functional.handoff;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Loopback test-only resolver and trust store; production address policy and default trust are unchanged. */
class HandoffHttpTransportTests {
    @Test void stoppedTransportRejectsNewWorkWithoutAffectingAnotherInstance() {
        var stopped=new HandoffHttpTransport();stopped.destroy();
        assertEquals("TRANSPORT_STOPPED",stopped.post("https://receiver.example.test",null,new byte[0]).error());
        assertThrows(IllegalArgumentException.class,()->stopped.validate("https://receiver.example.test"));
        var other=new HandoffHttpTransport(){protected InetAddress[] lookup(String host){return new InetAddress[0];}};
        try { assertEquals("UNSAFE_ADDRESS",other.post("https://receiver.example.test",null,new byte[0]).error()); }
        finally {other.destroy();}
    }
    static SSLContext context;
    @BeforeAll static void certificate()throws Exception {
        Path directory=Path.of(System.getProperty("plan.review.repo.root"),".build","http-tls-test");Files.createDirectories(directory);
        Path store=directory.resolve("receiver.p12");
        if(!Files.exists(store)){
            var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString(),"-genkeypair","-alias","receiver","-keyalg","RSA","-validity","2","-storetype","PKCS12","-keystore",store.toString(),"-storepass","local-test-only","-dname","CN=receiver.example.test","-ext","SAN=dns:receiver.example.test").redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
            assertTrue(command.waitFor(30,TimeUnit.SECONDS));assertEquals(0,command.exitValue());
        }
        var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,"local-test-only".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"local-test-only".toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),tm.getTrustManagers(),null);
    }
    static class LocalTransport extends HandoffHttpTransport {
        long limit=3000;boolean defaultTrust;
        protected Address resolve(URI uri)throws UnknownHostException{return new Address(uri,InetAddress.getByName("127.0.0.1"));}
        protected SSLSocketFactory sslFactory(){return defaultTrust?super.sslFactory():context.getSocketFactory();}
        protected long requestLimitMillis(){return limit;}
    }
    static class Receiver implements AutoCloseable {
        final SSLServerSocket server;final Thread thread;final AtomicInteger connections=new AtomicInteger();
        volatile String headers="";volatile byte[] body;volatile Exception error;
        Receiver(String response,long wait)throws Exception {
            server=(SSLServerSocket)context.getServerSocketFactory().createServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
            thread=Thread.ofVirtual().start(()->{try(var socket=(SSLSocket)server.accept()){
                connections.incrementAndGet();socket.setSoTimeout(5000);var in=socket.getInputStream();var bytes=new ByteArrayOutputStream();int c;
                while((c=in.read())>=0){bytes.write(c);if(bytes.size()>32768)throw new IOException();if(bytes.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n"))break;}
                headers=bytes.toString(StandardCharsets.ISO_8859_1);int size=0;for(String line:headers.split("\r\n"))if(line.startsWith("Content-Length:"))size=Integer.parseInt(line.substring(15).strip());
                body=in.readNBytes(size);if(wait>0)Thread.sleep(wait);
                socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            }catch(Exception e){error=e;}});
        }
        String url(){return "https://receiver.example.test:"+server.getLocalPort()+"/Path?b=secret&a=2";}
        public void close()throws Exception {server.close();thread.join(5500);}
    }
    @ParameterizedTest @ValueSource(ints={200,202,204,400,401,408,429,500,503})
    void actualTlsPostKeepsMethodQueryHeadersAndUtf8(int status)throws Exception {
        String response="HTTP/1.1 "+status+" Result\r\nContent-Length: 25\r\nRetry-After: 60\r\n\r\n{business_failed: true}";
        try(var server=new Receiver(response,0)){
            var transport=new LocalTransport();byte[] payload="{\"name\":\"中文\"}".getBytes(StandardCharsets.UTF_8);
            var result=transport.post(server.url(),"controlled-test-token",payload);
            assertEquals(status,result.status());assertTrue(server.headers.startsWith("POST /Path?b=secret&a=2 HTTP/1.1"));assertTrue(server.headers.contains("Authorization: Bearer controlled-test-token"));assertTrue(server.headers.contains("application/json; charset=utf-8"));assertArrayEquals(payload,server.body);assertEquals(1,server.connections.get());
            assertEquals(Set.of(408,429,500,503).contains(status),result.retryable());
        }
    }
    @Test void redirectNeverContactsLocation()throws Exception {
        try(var server=new Receiver("HTTP/1.1 302 Found\r\nLocation: https://127.0.0.1:1/private\r\nContent-Length: 0\r\n\r\n",0)){
            var result=new LocalTransport().post(server.url(),null,new byte[0]);assertEquals(302,result.status());assertFalse(result.retryable());assertEquals(1,server.connections.get());
        }
    }
    @Test void totalDeadlineClosesSlowResponse()throws Exception {
        try(var server=new Receiver("HTTP/1.1 204 No Content\r\n\r\n",1000)){
            var transport=new LocalTransport();transport.limit=300;long before=System.nanoTime();var result=transport.post(server.url(),null,new byte[0]);
            assertNull(result.status());assertTrue(result.retryable());assertTrue(result.unknown());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<1200);
        }
    }
    @Test void defaultTrustRejectsSelfSignedCertificate()throws Exception {
        try(var server=new Receiver("HTTP/1.1 204 No Content\r\n\r\n",0)){
            var transport=new LocalTransport();transport.defaultTrust=true;var result=transport.post(server.url(),null,new byte[0]);assertEquals("TLS_ERROR",result.error());assertFalse(result.retryable());assertNull(server.body);
        }
    }
    @Test void trustedCertificateStillRequiresOriginalHostname()throws Exception {
        try(var server=new Receiver("HTTP/1.1 204 No Content\r\n\r\n",0)){
            var result=new LocalTransport().post(server.url().replace("receiver.example.test","other.example.test"),null,new byte[0]);assertEquals("TLS_ERROR",result.error());assertNull(server.body);
        }
    }
    @Test void productionResolverRejectsLoopbackBeforeAnyPost(){var result=new HandoffHttpTransport().post("https://127.0.0.1:1/",null,new byte[0]);assertEquals("UNSAFE_ADDRESS",result.error());assertFalse(result.retryable());}
}
