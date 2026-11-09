package io.metersphere.functional.planreview;

import io.metersphere.functional.handoff.*;
import io.metersphere.sdk.util.JSON;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Opt-in foreground experiment: actual worker + 3 due plans + local slow TLS receiver, no production URLs. */
public class HandoffSchedulingProbe {
    static SSLContext tls(Path root)throws Exception {
        Path dir=root.resolve(".build/v05-scheduling-tls");Files.createDirectories(dir);Path file=dir.resolve("receiver.p12");
        if(!Files.exists(file)){
            var p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/keytool.exe").toString(),"-genkeypair","-alias","receiver","-keyalg","RSA","-validity","2","-storetype","PKCS12","-keystore",file.toString(),"-storepass","local-test-only","-dname","CN=receiver.example.test","-ext","SAN=dns:receiver.example.test").redirectErrorStream(true).redirectOutput(dir.resolve("keytool.log").toFile()).start();
            if(!p.waitFor(30,TimeUnit.SECONDS)||p.exitValue()!=0)throw new IllegalStateException("Test certificate generation failed");
        }
        KeyStore ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(file)){ks.load(in,"local-test-only".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,"local-test-only".toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);
        var ctx=SSLContext.getInstance("TLS");ctx.init(km.getKeyManagers(),tm.getTrustManagers(),null);return ctx;
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(System.getProperty("plan.review.repo.root"));var fixture=new HandoffPersistenceTests();fixture.setup();
        SSLContext context=tls(root);var received=new CopyOnWriteArrayList<Map<String,Object>>();var errors=new CopyOnWriteArrayList<String>();var count=new CountDownLatch(3);
        var scheduler=new ThreadPoolTaskScheduler(); // default pool size = 1, matching the repository's default taskScheduler.
        scheduler.setThreadNamePrefix("handoff-probe-");scheduler.setWaitForTasksToCompleteOnShutdown(true);scheduler.setAwaitTerminationSeconds(3);
        try(var listener=(SSLServerSocket)context.getServerSocketFactory().createServerSocket(0,10,InetAddress.getByName("127.0.0.1"))){
            Thread accept=Thread.ofVirtual().start(()->{try{for(int i=0;i<6;i++){
                try(var socket=(SSLSocket)listener.accept()){
                    socket.setSoTimeout(5000);var in=socket.getInputStream();var header=new ByteArrayOutputStream();int value;
                    while((value=in.read())>=0){header.write(value);if(header.size()>32768)throw new IOException();if(header.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n"))break;}
                    int length=0;for(String line:header.toString(StandardCharsets.ISO_8859_1).split("\r\n"))if(line.startsWith("Content-Length:"))length=Integer.parseInt(line.substring(15).strip());
                    var payload=JSON.parseObject(new String(in.readNBytes(length),StandardCharsets.UTF_8),Map.class);
                    received.add(Map.of("event",payload.get("event_id"),"receivedAt",System.currentTimeMillis(),"phase",i<3?"INITIAL_FAST_503":"RETRY_SLOW_204"));
                    if(i>=3)Thread.sleep(2500);String response=i<3?"HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\n\r\n":"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();if(i>=3)count.countDown();
                }
            }}catch(Exception e){errors.add(e.getClass().getSimpleName());}});
            var transport=new HandoffHttpTransport(){public void validate(String url){syntax(url);}protected Address resolve(URI uri)throws UnknownHostException{return new Address(uri,InetAddress.getByName("127.0.0.1"));}protected SSLSocketFactory sslFactory(){return context.getSocketFactory();}};
            var auth=new PlanReviewAuthorization(fixture.jdbc);var states=new PlanReviewPlanStateResolver(List.of());var locks=new PlanReviewPersistence(fixture.jdbc);var store=new HandoffStore(fixture.jdbc,locks);
            fixture.service=new HandoffService(store,new HandoffSourceReader(fixture.jdbc,states,auth,new PlanReviewFrozenReader(fixture.jdbc)),new HandoffAuthorization(fixture.jdbc,auth),locks,transport,new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32])),"isolated-test");fixture.service.setSourceBaseUrl("https://msp.example.test");
            fixture.worker=new HandoffWorker(store,fixture.service,fixture.tx.getTransactionManager());fixture.worker.setEnabled(true);
            fixture.save("https://receiver.example.test:"+listener.getLocalPort()+"/hook","NONE","");fixture.run(()->fixture.service.grant("project",fixture.revision(),true,"actor"));
            var app=new PlanReviewApplicationService(fixture.jdbc,locks,auth,fixture.tx.getTransactionManager(),states);
            for(int n=2;n<=3;n++){
                String plan="plan"+n,review="review"+n;
                fixture.jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES(?,'project','TEST_PLAN','UNDERWAY')",plan);
                fixture.jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES(?,'project','UNDERWAY')",review);
                fixture.jdbc.update("INSERT INTO test_plan_functional_case VALUES(?,?, 'a'),(?,?,'b')",plan+"a",plan,plan+"b",plan);
                var preview=fixture.run(()->app.preview(plan,review,"PAIR_EXISTING","actor"));var pair=fixture.run(()->app.confirm((String)preview.get("previewId"),"pair"+plan,true,"actor"));
                fixture.tx.executeWithoutResult(s->app.synchronize((String)pair.get("relationId"),(String)pair.get("generation")));
            }
            fixture.enable();fixture.jdbc.update("UPDATE case_review_functional_case SET status='PASS'");
            for(String suffix:List.of("","2","3")){
                String review="review"+suffix,plan="plan"+suffix;var preview=fixture.run(()->fixture.lifecycle.preview("review",review,"COMPLETE","actor"));fixture.run(()->fixture.lifecycle.confirm((String)preview.get("previewId"),true,"actor"));fixture.tx.executeWithoutResult(s->fixture.service.capture("project",plan,true));
            }
            if(fixture.count("hifox_http_delivery")!=3)throw new IllegalStateException("Expected 3 real qualified source candidates");
            for(String key:fixture.jdbc.queryForList("SELECT business_key FROM hifox_http_delivery ORDER BY business_key",String.class))fixture.worker.dispatch("project",key);
            if(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE state='RETRY' AND attempts_used=1",Integer.class)!=3)throw new IllegalStateException("Expected 3 actual HTTP 503 retry records");
            long dueAt=fixture.jdbc.queryForObject("SELECT MAX(next_at) FROM hifox_http_delivery",Long.class)+200;
            fixture.jdbc.update("UPDATE hifox_http_delivery SET next_at=?",dueAt);long readyAt=System.currentTimeMillis();
            var sentinelCount=new AtomicInteger();var maxSentinelLag=new AtomicLong();scheduler.initialize();
            scheduler.scheduleWithFixedDelay(fixture.worker::tick,Duration.ofMillis(1000));
            scheduler.scheduleAtFixedRate(()->{long expected=readyAt+sentinelCount.getAndIncrement()*200L;maxSentinelLag.accumulateAndGet(System.currentTimeMillis()-expected,Math::max);},Duration.ofMillis(200));
            if(!count.await(25,TimeUnit.SECONDS))throw new IllegalStateException("Slow receiver experiment timed out");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<deadline&&fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE state='SUCCESS'",Integer.class)<3)Thread.sleep(20);
            scheduler.shutdown();accept.join(2000);
            var attempts=fixture.jdbc.queryForList("SELECT d.plan_id,a.sequence_no,a.started_at,a.ended_at FROM hifox_http_attempt a JOIN hifox_http_delivery d ON d.business_key=a.business_key WHERE a.sequence_no=2 ORDER BY a.started_at");
            long maxDelay=0;for(var a:attempts){long delay=((Number)a.get("started_at")).longValue()-dueAt;a.put("retryStartDelayMs",delay);maxDelay=Math.max(maxDelay,delay);}
            var result=new LinkedHashMap<String,Object>();result.put("poolSize",1);result.put("dueRecords",3);result.put("receiverDelayMs",2500);result.put("workerFixedDelayMs",1000);result.put("maxRetryStartDelayMs",maxDelay);result.put("maxSharedTaskLagMs",maxSentinelLag.get());result.put("withinFiveSecondRetryTarget",maxDelay<=5000);result.put("commonRetryDueAt",dueAt);result.put("actualPosts",received.size());result.put("attempts",attempts);result.put("received",received);result.put("errors",errors);result.put("successes",fixture.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE state='SUCCESS'",Integer.class));
            Files.writeString(root.resolve(".build/handoff-v05-scheduling.json"),JSON.toJSONString(result));System.out.println(JSON.toJSONString(result));
            if(!errors.isEmpty()||attempts.size()!=3||((Number)result.get("successes")).intValue()!=3)throw new IllegalStateException("Experiment incomplete");
        } finally {scheduler.shutdown();fixture.closeOwnedInMemoryDatabase();}
    }
}
