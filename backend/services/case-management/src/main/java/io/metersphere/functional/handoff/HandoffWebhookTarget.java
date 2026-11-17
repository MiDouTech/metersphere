package io.metersphere.functional.handoff;

/** Production HTTP boundary; reception business actions are outside MSP. */
public interface HandoffWebhookTarget {
    void validate(String url);
    HandoffHttpTransport.Result post(String url,String bearerToken,byte[] payload);
}
