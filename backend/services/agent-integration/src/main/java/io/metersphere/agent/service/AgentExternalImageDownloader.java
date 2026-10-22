package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded public-image fetcher. DNS results are checked and pinned to the actual connection. */
@Service
public class AgentExternalImageDownloader {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Semaphore slots = new Semaphore(4);
    private final ExecutorService dns = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> { var t=new Thread(r,"external-image-dns"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r,"external-image-deadline"); t.setDaemon(true); return t;
    });
    public record Image(byte[] bytes, String contentType, String fileName) { }

    public Image download(String value, String requestedName) {
        if (!slots.tryAcquire()) throw failure("EXTERNAL_IMAGE_RATE_LIMIT");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        try {
            URI uri=validateUri(value);
            for(int redirects=0;redirects<=3;redirects++) {
                remaining(deadline);
                InetAddress[] addresses=resolve(uri.getHost(),deadline);
                for(var address:addresses) requirePublic(address);
                final String host=uri.getHost();
                DnsResolver pinned=new DnsResolver() {
                    public InetAddress[] resolve(String target) throws UnknownHostException {
                        if(!host.equalsIgnoreCase(target)) throw new UnknownHostException("Unapproved host");
                        return addresses.clone();
                    }
                    public String resolveCanonicalHostname(String target) throws UnknownHostException {
                        resolve(target); return host;
                    }
                };
                try(var manager=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(pinned)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(3))
                                .setSocketTimeout(Timeout.ofSeconds(5)).build()).build();
                    var client=HttpClients.custom().setConnectionManager(manager).disableRedirectHandling()
                            .disableAutomaticRetries().disableContentCompression().disableCookieManagement().build()) {
                    var request=new HttpGet(uri);
                    request.setHeader("Accept","image/png,image/jpeg");
                    request.setHeader("Accept-Encoding","identity");
                    request.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMilliseconds(Math.min(5000,remaining(deadline))))
                            .setConnectionRequestTimeout(Timeout.ofSeconds(1)).build());
                    var cancel=deadlines.schedule(request::cancel,remaining(deadline),TimeUnit.MILLISECONDS);
                    try {
                        Object result=client.execute(request,response -> {
                            int code=response.getCode();
                            if(Set.of(301,302,303,307,308).contains(code)) {
                                var location=response.getFirstHeader("Location");
                                if(location==null) throw failure("EXTERNAL_IMAGE_FETCH_FAILED");
                                return location.getValue();
                            }
                            if(code!=200 || response.getEntity()==null) throw failure("EXTERNAL_IMAGE_FETCH_FAILED");
                            var encoding=response.getFirstHeader("Content-Encoding");
                            if(encoding!=null && !"identity".equalsIgnoreCase(encoding.getValue())) throw failure("EXTERNAL_IMAGE_TYPE_UNSUPPORTED");
                            if(response.getEntity().getContentLength()>MAX_BYTES) throw failure("EXTERNAL_IMAGE_TOO_LARGE");
                            try(var in=response.getEntity().getContent(); var out=new ByteArrayOutputStream()) {
                                byte[] buffer=new byte[8192]; int n;
                                while((n=in.read(buffer))!=-1) {
                                    remaining(deadline);
                                    if(out.size()+n>MAX_BYTES) throw failure("EXTERNAL_IMAGE_TOO_LARGE");
                                    out.write(buffer,0,n);
                                }
                                return inspect(out.toByteArray(),requestedName);
                            }
                        });
                        if(result instanceof Image image) return image;
                        if(redirects==3) throw failure("EXTERNAL_IMAGE_FETCH_FAILED");
                        uri=validateUri(uri.resolve((String)result).toString());
                    } finally { cancel.cancel(false); }
                }
            }
            throw failure("EXTERNAL_IMAGE_FETCH_FAILED");
        } catch(MSException error) { throw error; }
        catch(SocketTimeoutException | TimeoutException error) { throw failure("EXTERNAL_IMAGE_TIMEOUT"); }
        catch(Exception error) {
            if(System.nanoTime()>=deadline) throw failure("EXTERNAL_IMAGE_TIMEOUT");
            throw failure("EXTERNAL_IMAGE_FETCH_FAILED");
        } finally { slots.release(); }
    }

    private InetAddress[] resolve(String host,long deadline) throws Exception {
        Future<InetAddress[]> future;
        try { future=dns.submit(() -> InetAddress.getAllByName(host)); }
        catch(RejectedExecutionException busy) { throw failure("EXTERNAL_IMAGE_RATE_LIMIT"); }
        try {
            var addresses=future.get(Math.min(3000,remaining(deadline)),TimeUnit.MILLISECONDS);
            if(addresses.length==0) throw failure("EXTERNAL_IMAGE_FETCH_FAILED");
            return addresses;
        } finally { future.cancel(true); }
    }

    static URI validateUri(String value) {
        try {
            var uri=URI.create(value);
            if(value.length()>8192 || !Set.of("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawFragment()!=null
                    || !(uri.getPort()==-1 || uri.getPort()==80 || uri.getPort()==443)) throw failure("EXTERNAL_IMAGE_URL_INVALID");
            return uri;
        } catch(MSException e) { throw e; }
        catch(Exception e) { throw failure("EXTERNAL_IMAGE_URL_INVALID"); }
    }

    static void requirePublic(InetAddress address) {
        byte[] b=address.getAddress();
        if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) throw failure("EXTERNAL_IMAGE_TARGET_FORBIDDEN");
        if(b.length==4) {
            int a=b[0]&255,c=b[1]&255,d=b[2]&255;
            if(a==0 || a==10 || a==127 || a>=224 || (a==100 && c>=64 && c<=127)
                    || (a==169 && c==254) || (a==172 && c>=16 && c<=31) || (a==192 && c==168)
                    || (a==192 && c==0) || (a==192 && c==2) || (a==198 && (c==18 || c==19 || c==51))
                    || (a==203 && c==0 && d==113)) throw failure("EXTERNAL_IMAGE_TARGET_FORBIDDEN");
        } else if((b[0]&0xe0)!=0x20 || (b[0]==0x20 && b[1]==2)
                || (b[0]==0x20 && b[1]==1 && ((b[2]==0 && b[3]==0) || (b[2]&255)==0x0d && (b[3]&255)==0xb8))) {
            throw failure("EXTERNAL_IMAGE_TARGET_FORBIDDEN");
        }
    }

    static Image inspect(byte[] bytes,String requestedName) {
        if(bytes.length==0) throw failure("EXTERNAL_IMAGE_INVALID");
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw failure("EXTERNAL_IMAGE_TYPE_UNSUPPORTED");
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("png","jpeg","jpg").contains(format)) throw failure("EXTERNAL_IMAGE_TYPE_UNSUPPORTED");
                reader.setInput(input,true,true);
                long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                if(pixels<1 || pixels>20_000_000) throw failure("EXTERNAL_IMAGE_TOO_LARGE");
                if(reader.read(0)==null) throw failure("EXTERNAL_IMAGE_INVALID");
                boolean png="png".equals(format);
                String name=requestedName==null?"external-image":requestedName;
                name=name.replaceAll("[\\\\/\\p{Cntrl}\\\"]","_").replaceAll("\\.[^.]*$","");
                if(name.isBlank()) name="external-image";
                name=name.substring(0,Math.min(180,name.length()))+(png?".png":".jpg");
                return new Image(bytes,png?"image/png":"image/jpeg",name);
            } finally { reader.dispose(); }
        } catch(MSException e) { throw e; }
        catch(Exception e) { throw failure("EXTERNAL_IMAGE_INVALID"); }
    }
    private static long remaining(long deadline) {
        long ms=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
        if(ms<=0) throw failure("EXTERNAL_IMAGE_TIMEOUT");
        return ms;
    }
    private static MSException failure(String code) { return new MSException(code); }
    @PreDestroy public void close() { dns.shutdownNow(); deadlines.shutdownNow(); }
}
