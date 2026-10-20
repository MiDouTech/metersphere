package io.metersphere.agent.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * An independently operated evidence reviewer. Disabled until an operator explicitly
 * configures the approved service; absence or failure must never mean approval.
 * No endpoint, credential, or verification verdict is accepted from an Agent request.
 */
@Service
public class EvidenceContentReviewer {
    private final String endpoint;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    public record Review(boolean supported, String code, String reviewerVersion, String requestHash) {}

    public Map<String,Object> status() {
        return Map.of("configured",!endpoint.isBlank() && !token.isBlank(),
                "verification","PER_SUBMISSION","unavailableBehavior","NEEDS_REVIEW",
                "message","配置状态不代表核验服务可用；每次提交独立核验，未通过不会发布正式结果");
    }

    public EvidenceContentReviewer(
            @Value("${agent.evidence-review.endpoint:}") String endpoint,
            @Value("${agent.evidence-review.token:}") String token) {
        this.endpoint = endpoint;
        this.token = token;
    }

    public Review review(Map<String, Object> submission) {
        if (endpoint.isBlank() || token.isBlank()) return unavailable("EVIDENCE_REVIEW_NOT_CONFIGURED");
        try {
            URI uri = URI.create(endpoint);
            boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (uri.getUserInfo() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))) {
                return unavailable("EVIDENCE_REVIEW_CONFIGURATION_INVALID");
            }
            byte[] body = json.writeValueAsBytes(submission);
            if (body.length > 72 * 1024 * 1024) return unavailable("EVIDENCE_REVIEW_LIMIT_EXCEEDED");
            String hash = DigestUtils.sha256Hex(body);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("X-Evidence-Request-Hash", hash)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = client.send(request, info -> new LimitedBody());
            {
                byte[] bytes = response.body();
                if (response.statusCode() != 200 || bytes.length > 16384) return unavailable("EVIDENCE_REVIEW_UNAVAILABLE");
                var result = json.readTree(bytes);
                String version = result.path("reviewerVersion").asText();
                if (!hash.equals(result.path("requestHash").asText()) || !version.matches("[a-zA-Z0-9_.:-]{1,128}")) {
                    return unavailable("EVIDENCE_REVIEW_RESPONSE_INVALID");
                }
                // Approval covers every submitted step and exact artifact digest. A partial
                // response or an unrecognized status is never interpreted as acceptance.
                var submitted = json.valueToTree(submission).path("steps");
                var reviewed = result.path("steps");
                if (!reviewed.isArray() || reviewed.size() != submitted.size()) return unavailable("EVIDENCE_REVIEW_RESPONSE_INVALID");
                for (int i = 0; i < submitted.size(); i++) {
                    var expected = submitted.get(i);
                    var observed = reviewed.get(i);
                    if (!expected.path("stepNum").equals(observed.path("stepNum"))
                            || !expected.path("actual").equals(observed.path("observedActual"))
                            || !expected.path("evidenceHashes").equals(observed.path("evidenceHashes"))) {
                        return unavailable("EVIDENCE_REVIEW_RESPONSE_INVALID");
                    }
                    if (!"SUPPORTED".equals(observed.path("status").asText())) {
                        return new Review(false, "EVIDENCE_CONTENT_NOT_SUPPORTED", version, hash);
                    }
                }
                if (submitted.isEmpty()) return unavailable("EVIDENCE_REVIEW_RESPONSE_INVALID");
                return new Review(true, "EVIDENCE_CONTENT_SUPPORTED", version, hash);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return unavailable("EVIDENCE_REVIEW_UNAVAILABLE");
        } catch (Exception error) {
            // Never expose a remote response, token, endpoint, or network stack trace.
            return unavailable("EVIDENCE_REVIEW_UNAVAILABLE");
        }
    }

    private Review unavailable(String code) { return new Review(false, code, "", ""); }

    /** Bound the response while it arrives; request timeout covers body completion. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(bytes.size()+buffer.remaining()>16384){
                    subscription.cancel();
                    result.completeExceptionally(new IllegalArgumentException("Review response limit exceeded"));
                    return;
                }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure){result.completeExceptionally(failure);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
