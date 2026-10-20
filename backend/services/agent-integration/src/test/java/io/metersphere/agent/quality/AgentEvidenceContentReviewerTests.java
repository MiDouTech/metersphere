package io.metersphere.agent.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Protocol tests only; this is not a visual-model accuracy evaluation. */
class AgentEvidenceContentReviewerTests {
    @Test void unconfiguredOrUnsafeReviewerDoesNotApprove() {
        assertFalse(new EvidenceContentReviewer("","").review(Map.of()).supported());
        assertFalse(new EvidenceContentReviewer("http://public.example/review","key").review(Map.of()).supported());
    }
    @Test void independentResponseMustCoverEveryActualAndEvidenceDigest() throws Exception {
        var json=new ObjectMapper();
        var request=Map.<String,Object>of("steps",List.of(Map.of("stepNum",1,"actual","visible","evidenceHashes",List.of("digest"))));
        for(String mode:List.of("supported","wrong-hash","wrong-observation","insufficient","partial")) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/review",exchange->{
                exchange.getRequestBody().readAllBytes();
                String hash=exchange.getRequestHeaders().getFirst("X-Evidence-Request-Hash");
                var step=Map.of("stepNum",1,"observedActual",mode.equals("wrong-observation")?"different":"visible",
                        "evidenceHashes",List.of("digest"),"status",mode.equals("insufficient")?"INSUFFICIENT":"SUPPORTED");
                byte[] body=json.writeValueAsBytes(Map.of("requestHash",mode.equals("wrong-hash")?"wrong":hash,
                        "reviewerVersion","fixture-1","steps",mode.equals("partial")?List.of():List.of(step)));
                exchange.sendResponseHeaders(200,body.length);
                try(var output=exchange.getResponseBody()){output.write(body);}
            });
            server.start();
            try {
                var result=new EvidenceContentReviewer("http://127.0.0.1:"+server.getAddress().getPort()+"/review","test-only").review(request);
                assertEquals(mode.equals("supported"),result.supported(),mode);
            } finally {server.stop(0);}
        }
    }
}
