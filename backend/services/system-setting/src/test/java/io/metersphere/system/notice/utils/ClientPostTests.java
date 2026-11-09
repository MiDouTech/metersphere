package io.metersphere.system.notice.utils;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientPostTests {
    static class Client extends CloseableHttpClient {
        int attempts, closed, responsesClosed; boolean failExecute, failClose;
        @Override protected CloseableHttpResponse doExecute(HttpHost host, ClassicHttpRequest request, HttpContext context) throws IOException {
            attempts++;
            if (failExecute) throw new IOException("private-url-must-not-be-logged");
            return CloseableHttpResponse.adapt(new BasicClassicHttpResponse(503) {
                @Override public void close() throws IOException {
                    responsesClosed++;
                    if (failClose) throw new IOException("private-close-error");
                    super.close();
                }
            });
        }
        @Override public void close() { closed++; }
        @Override public void close(CloseMode mode) { close(); }
    }

    @Test void connectionFailureDoesNotThrowASecondNullPointerOrCloseBorrowedClient() {
        var client = new Client(); client.failExecute = true;
        assertDoesNotThrow(() -> ClientPost.executeClient("https://receiver.example.test", client, Map.of("message", "中文")));
        assertEquals(1, client.attempts); assertEquals(0, client.closed); assertEquals(0, client.responsesClosed);
    }
    @Test void sharedClientCanBeReusedAndEveryResponseIsClosed() {
        var client = new Client();
        for (int n=0;n<2;n++) ClientPost.executeClient("https://receiver.example.test", client, Map.of("message", "ok"));
        assertEquals(2, client.attempts); assertEquals(2, client.responsesClosed); assertEquals(0, client.closed);
    }
    @Test void ownedClientClosesEvenWhenExecuteFails() {
        var client = new Client(); client.failExecute = true;
        assertDoesNotThrow(() -> ClientPost.executeOwned("https://receiver.example.test", client, "{}"));
        assertEquals(1, client.closed); assertEquals(1, client.attempts);
    }
    @Test void ownedClientClosesEvenWhenResponseCloseFails() {
        var client = new Client(); client.failClose = true;
        assertDoesNotThrow(() -> ClientPost.executeOwned("https://receiver.example.test", client, "{}"));
        assertEquals(1, client.closed); assertEquals(1, client.responsesClosed); assertEquals(1, client.attempts);
    }
    @Test void invalidUrlDoesNotAttemptNetworkAndStillClosesOwnedClient() {
        var client = new Client();
        ClientPost.executeOwned("https://[invalid", client, "{}");
        assertEquals(0, client.attempts); assertEquals(1, client.closed);
    }
}
