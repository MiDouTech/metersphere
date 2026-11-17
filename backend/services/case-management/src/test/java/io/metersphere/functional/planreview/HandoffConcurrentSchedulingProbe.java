package io.metersphere.functional.planreview;

import io.metersphere.functional.handoff.*;
import io.metersphere.sdk.util.JSON;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit foreground acceptance experiment. Bounded concurrent TLS receiver, real named Spring schedulers and worker. */
public class HandoffConcurrentSchedulingProbe {
    @Configuration @EnableScheduling
    static class ProbeConfiguration {
        @Bean(name="taskScheduler") ThreadPoolTaskScheduler ordinary(){var pool=new ThreadPoolTaskScheduler();pool.setPoolSize(1);pool.setThreadNamePrefix("ordinary-probe-");pool.setWaitForTasksToCompleteOnShutdown(true);pool.setAwaitTerminationSeconds(2);return pool;}
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(System.getProperty("plan.review.repo.root"));var results=new ArrayList<Map<String,Object>>();
        results.add(experiment(root,"same-project-3",3,false,2500));
        results.add(experiment(root,"same-project-4",4,false,2500));
        results.add(experiment(root,"mixed-projects-slow-8s",3,true,8000));
        Files.writeString(root.resolve(".build/handoff-v06-concurrent-scheduling.json"),JSON.toJSONString(results));
    }
    static Map<String,Object> experiment(Path root,String name,int size,boolean separate,int slow)throws Exception {
        var t=new HandoffPersistenceTests();t.setup();SSLContext tls=HandoffSchedulingProbe.tls(root);
        var received=new CopyOnWriteArrayList<Map<String,Object>>();var errors=new CopyOnWriteArrayList<String>();
        var perEvent=new ConcurrentHashMap<String,AtomicInteger>();var bodyHashes=new ConcurrentHashMap<String,String>();var complete=new CountDownLatch(size);
        var active=new AtomicInteger();var peak=new AtomicInteger();var clientActive=new AtomicInteger();var clientPeak=new AtomicInteger();
        var receiver=new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(4),task->new Thread(task,"tls-receiver"),new ThreadPoolExecutor.AbortPolicy());
        var context=new AnnotationConfigApplicationContext();
        try(var listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,16,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(35000);
            Thread accept=Thread.ofVirtual().start(()->{try{for(int i=0;i<size*2;i++){
                var socket=(SSLSocket)listener.accept();receiver.execute(()->{try(socket){
                    socket.setSoTimeout(5000);var in=socket.getInputStream();var header=new ByteArrayOutputStream();int c;
                    while((c=in.read())>=0){header.write(c);if(header.size()>32768)throw new IOException("HEADERS_TOO_LARGE");if(header.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n"))break;}
                    int length=0;for(String line:header.toString(StandardCharsets.ISO_8859_1).split("\r\n"))if(line.startsWith("Content-Length:"))length=Integer.parseInt(line.substring(15).strip());
                    String body=new String(in.readNBytes(length),StandardCharsets.UTF_8);var data=JSON.parseObject(body,Map.class);String event=Objects.toString(data.get("event_id"));var plan=(Map<?,?>)data.get("plan");var source=(Map<?,?>)data.get("source");
                    String hash=HandoffPolicy.hash(body),old=bodyHashes.putIfAbsent(event,hash);if(old!=null&&!old.equals(hash))errors.add("PAYLOAD_DRIFT");int attempt=perEvent.computeIfAbsent(event,k->new AtomicInteger()).incrementAndGet();
                    received.add(new LinkedHashMap<>(Map.of("event",event,"plan",plan.get("id"),"project",source.get("project_id"),"attempt",attempt,"receivedAt",System.currentTimeMillis(),"payloadHash",hash)));
                    if(attempt==2){int n=active.incrementAndGet();peak.accumulateAndGet(n,Math::max);try{Thread.sleep(separate&&!"plan".equals(plan.get("id"))?500:slow);}finally{active.decrementAndGet();}}
                    String response=attempt==1?"HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\n\r\n":"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();if(attempt==2)complete.countDown();
                }catch(Exception error){errors.add(error.getClass().getSimpleName());}});
            }}catch(Exception error){errors.add(error.getClass().getSimpleName());}});
            var transport=new HandoffHttpTransport(){
                public void validate(String url){syntax(url);}
                protected Address resolve(URI uri)throws UnknownHostException{return new Address(uri,InetAddress.getByName("127.0.0.1"));}
                protected SSLSocketFactory sslFactory(){return tls.getSocketFactory();}
                public Result post(String url,String token,byte[] body){boolean counted=Thread.currentThread().getName().startsWith("handoff-http-");if(counted)clientPeak.accumulateAndGet(clientActive.incrementAndGet(),Math::max);try{return super.post(url,token,body);}finally{if(counted)clientActive.decrementAndGet();}}
            };
            HandoffWorkerTestSources.prepare(t,size,separate,transport,"https://receiver.example.test:"+listener.getLocalPort()+"/hook");
            for(var d:t.jdbc.queryForList("SELECT project_id,business_key FROM hifox_http_delivery ORDER BY business_key"))t.worker.dispatch(d.get("project_id").toString(),d.get("business_key").toString());
            if(t.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE state='RETRY' AND attempts_used=1",Integer.class)!=size)throw new IllegalStateException("Expected actual 503 retry records");
            long due=t.jdbc.queryForObject("SELECT MAX(next_at) FROM hifox_http_delivery",Long.class)+200;t.jdbc.update("UPDATE hifox_http_delivery SET next_at=?",due);
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("probe",Map.of("metersphere.hifox-handoff.http-worker-enabled","true")));
            context.register(ProbeConfiguration.class,HandoffSchedulingConfiguration.class);context.registerBean(HandoffWorker.class,()->t.worker);context.refresh();
            var ordinary=context.getBean("taskScheduler",ThreadPoolTaskScheduler.class);long sentinelStart=System.currentTimeMillis();var sentinelCount=new AtomicInteger();var maxLag=new AtomicLong();
            ordinary.scheduleAtFixedRate(()->{long expected=sentinelStart+sentinelCount.getAndIncrement()*200L;maxLag.accumulateAndGet(System.currentTimeMillis()-expected,Math::max);},Duration.ofMillis(200));
            if(!complete.await(35,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent receiver deadline exceeded");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<deadline&&t.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE state='SUCCESS'",Integer.class)<size)Thread.sleep(20);
            var rows=t.jdbc.queryForList("SELECT d.plan_id,d.project_id,a.sequence_no,a.started_at,a.ended_at FROM hifox_http_attempt a JOIN hifox_http_delivery d ON d.business_key=a.business_key WHERE a.sequence_no=2 ORDER BY a.started_at");
            long maxStart=0,maxReceive=0;boolean early=false;
            for(var a:rows){long delay=((Number)a.get("started_at")).longValue()-due;a.put("retryStartDelayMs",delay);maxStart=Math.max(maxStart,delay);early|=delay<0;}
            for(var a:received)if(((Number)a.get("attempt")).intValue()==2){long delay=((Number)a.get("receivedAt")).longValue()-due;a.put("receiveDelayMs",delay);maxReceive=Math.max(maxReceive,delay);early|=delay<0;}
            var result=new LinkedHashMap<String,Object>();result.put("scenario",name);result.put("time",Instant.now().toString());result.put("concurrency",4);result.put("queueCapacity",4);result.put("records",size);result.put("receiverSlowDelayMs",slow);result.put("separateProjects",separate);result.put("commonRetryDueAt",due);result.put("maxRetryStartDelayMs",maxStart);result.put("maxReceiveDelayMs",maxReceive);result.put("maxOrdinarySchedulerLagMs",maxLag.get());result.put("receiverPeak",peak.get());result.put("clientPeak",clientPeak.get());result.put("actualPosts",received.size());result.put("early",early);result.put("attempts",rows);result.put("received",received);result.put("errors",errors);result.put("statistics",t.worker.statistics());result.put("successes",t.jdbc.queryForObject("SELECT COUNT(*) FROM hifox_http_delivery WHERE state='SUCCESS'",Integer.class));
            boolean pass=!early&&maxStart<=5000&&maxReceive<=5000&&maxLag.get()<1000&&errors.isEmpty()&&rows.size()==size&&received.size()==size*2&&peak.get()>=size&&((Number)result.get("successes")).intValue()==size;
            result.put("samplePassed",pass);Files.writeString(root.resolve(".build/handoff-v06-"+name+".json"),JSON.toJSONString(result));System.out.println(JSON.toJSONString(result));
            if(!pass)throw new IllegalStateException("Concurrent scheduling sample failed: "+name);
            context.close();accept.join(2000);return result;
        } finally {context.close();t.closeOwnedInMemoryDatabase();receiver.shutdownNow();receiver.awaitTermination(3,TimeUnit.SECONDS);}
    }
}
