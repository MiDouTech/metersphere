package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;

/** A write must carry the revision the editor actually read. */
final class AgentConfigurationVersion {
    private AgentConfigurationVersion() { }

    static int require(Integer version) {
        if (version == null || version < 0) {
            throw new MSException("CONFIGURATION_VERSION_REQUIRED");
        }
        return version;
    }
}
