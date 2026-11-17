package io.metersphere.functional.handoff;

import org.springframework.stereotype.Component;
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One pinned TLS connection, one POST, no redirects, proxies or transparent retries. */
@Component
public class HandoffHttpTransport implements HandoffWebhookTarget, org.springframework.beans.factory.DisposableBean {
    public record Result(Integer status, String error, boolean retryable, boolean unknown, long retryAfter) { }
    public record Address(URI uri, InetAddress address) { }
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    protected long requestLimitMillis() { return 30_000; }

    public static URI syntax(String input) {
        if (input == null || input.length() > 4096 || input.codePoints().anyMatch(c -> Character.isISOControl(c)))
            throw new IllegalArgumentException("接收地址格式不安全或超过4096字符");
        try {
            URI uri = new URI(input.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1)
                throw new IllegalArgumentException();
            return uri;
        } catch (Exception invalid) { throw new IllegalArgumentException("请填写合法的HTTPS接收地址"); }
    }
    public static boolean publicAddress(InetAddress address) {
        byte[] b = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        int a = b[0] & 255, c = b[1] & 255;
        if (b.length == 4) {
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 100 && c >= 64 && c <= 127
                    || a == 169 && c == 254 || a == 172 && c >= 16 && c <= 31 || a == 192 && (c == 0 || c == 168)
                    || a == 192 && c == 88 && (b[2] & 255) == 99 || a == 192 && c == 2
                    || a == 198 && (c == 18 || c == 19 || c == 51) || a == 203 && c == 0 && (b[2] & 255) == 113);
        }
        // Only global unicast; exclude IETF protocol, documentation, 6to4 and reserved blocks.
        return a >= 0x20 && a <= 0x3f && !(a == 0x3f && c == 0xff) && !(a == 0x20 && (c == 1 && (b[2] & 255) < 2
                || c == 1 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8 || c == 2));
    }
    protected InetAddress[] lookup(String host) throws UnknownHostException { return InetAddress.getAllByName(host); }
    protected Address resolve(URI uri) throws UnknownHostException {
        InetAddress[] addresses = lookup(uri.getHost());
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(a -> !publicAddress(a)))
            throw new SecurityException("UNSAFE_ADDRESS");
        return new Address(uri, addresses[0]);
    }
    public void validate(String input) {
        URI uri = syntax(input);
        Future<Address> future;
        try { future = io.submit(() -> resolve(uri)); }
        catch (RejectedExecutionException stopped) { throw new IllegalArgumentException("Webhook传输已停止"); }
        try { future.get(requestLimitMillis(), TimeUnit.MILLISECONDS); }
        catch (Exception invalid) { throw new IllegalArgumentException("接收地址安全校验失败，请检查公网DNS及地址"); }
        finally { future.cancel(true); }
    }
    public Result post(String input, String token, byte[] body) {
        AtomicReference<Socket> socket = new AtomicReference<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestLimitMillis());
        Future<Result> call;
        try { call = io.submit(() -> exchange(syntax(input), token, body, socket, deadline)); }
        catch (RejectedExecutionException stopped) { return new Result(null,"TRANSPORT_STOPPED",false,false,0); }
        try { return call.get(requestLimitMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException timeout) { return new Result(null,"TIMEOUT",true,true,0); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return new Result(null,"INTERRUPTED",true,true,0); }
        catch (CancellationException stopped) { return new Result(null,"INTERRUPTED",true,true,0); }
        catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof SecurityException || cause instanceof IllegalArgumentException)
                return new Result(null,"UNSAFE_ADDRESS",false,false,0);
            if (cause instanceof SocketTimeoutException) return new Result(null,"TIMEOUT",true,true,0);
            if (cause instanceof SSLException) return new Result(null,"TLS_ERROR",false,false,0);
            if (cause instanceof UnknownHostException) return new Result(null,"DNS_ERROR",true,false,0);
            return new Result(null,"NETWORK_ERROR",true,true,0);
        } finally {
            call.cancel(true);
            close(socket.getAndSet(null));
        }
    }
    /** Per-bean lifetime; stopping one context must not stop another context's transport. */
    @Override public void destroy() { io.shutdownNow(); }
    protected SSLSocketFactory sslFactory() { return (SSLSocketFactory) SSLSocketFactory.getDefault(); }
    private Result exchange(URI uri,String token,byte[] body,AtomicReference<Socket> active,long deadline) throws Exception {
        Address target = resolve(uri); // Every attempt resolves and validates ALL answers before pinning one.
        Socket raw = new Socket(); active.set(raw);
        try {
            check(deadline); raw.connect(new InetSocketAddress(target.address(), uri.getPort() == -1 ? 443 : uri.getPort()), remaining(deadline));
            check(deadline);
            SSLSocket tls = (SSLSocket) sslFactory().createSocket(raw, uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort(), true);
            active.set(tls);
            SSLParameters parameters = tls.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters); tls.setSoTimeout(remaining(deadline)); tls.startHandshake(); check(deadline);
            String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            // URI ASCII encoding preserves existing escapes and query order.
            path = URI.create(path).toASCIIString();
            String host = uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            String header = "POST " + path + " HTTP/1.1\r\nHost: " + host
                    + "\r\nContent-Type: application/json; charset=utf-8\r\nAccept: application/json\r\nConnection: close\r\nContent-Length: " + body.length
                    + (token == null || token.isEmpty() ? "" : "\r\nAuthorization: Bearer " + token) + "\r\n\r\n";
            check(deadline); OutputStream out = tls.getOutputStream(); out.write(header.getBytes(StandardCharsets.US_ASCII)); out.write(body); out.flush();
            InputStream in = tls.getInputStream();
            int consumed = 0, status; String retry = null;
            do {
                String line = line(in,deadline,tls); consumed += line.length();
                if (!line.matches("HTTP/1\\.[01] [0-9]{3}( .*)?")) throw new IOException("INVALID_RESPONSE");
                status = Integer.parseInt(line.substring(9,12));
                for (;;) {
                    line = line(in,deadline,tls); consumed += line.length()+2;
                    if (consumed > 32768) throw new IOException("RESPONSE_HEADERS_TOO_LARGE");
                    if (line.isEmpty()) break;
                    if (line.toLowerCase(Locale.ROOT).startsWith("retry-after:")) retry = line.substring(12).strip();
                }
            } while (status >= 100 && status < 200 && status != 101);
            boolean ok = status >= 200 && status <= 299;
            return new Result(status,ok?null:"HTTP_"+status,!ok && (status==408 || status==429 || status>=500 && status<=599),false,
                    status==429 || status==503 ? retryAfter(retry,System.currentTimeMillis()) : 0);
        } finally { close(active.getAndSet(null)); close(raw); }
    }
    private static String line(InputStream in,long deadline,Socket socket) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (;;) {
            socket.setSoTimeout(remaining(deadline)); int c = in.read();
            if (c < 0) throw new EOFException();
            if (bytes.size() > 8192) throw new IOException("RESPONSE_HEADERS_TOO_LARGE");
            if (c == '\n') return bytes.toString(StandardCharsets.ISO_8859_1).replaceFirst("\\r$", "");
            bytes.write(c);
        }
    }
    private static int remaining(long deadline) throws SocketTimeoutException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw new SocketTimeoutException();
        return (int)Math.min(remaining,Integer.MAX_VALUE);
    }
    private static void check(long deadline) throws SocketTimeoutException { remaining(deadline); }
    private static void close(Socket socket) { if(socket!=null)try{socket.close();}catch(IOException ignored){ } }
    public static long retryAfter(String value,long now) {
        if(value==null)return 0;
        try {
            long target = value.matches("[0-9]+") ? Math.addExact(now,Math.multiplyExact(Long.parseLong(value),1000))
                    : ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            return Math.max(now,target);
        } catch (RuntimeException invalid) { return 0; }
    }
}
