package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetRefDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

final class AgentEnvironmentAssetBinding {
    private AgentEnvironmentAssetBinding() { }

    static List<TestAssetRefDTO> normalize(List<TestAssetRefDTO> source, String boundAssetId) {
        String assetId = StringUtils.trimToNull(boundAssetId);
        if (assetId == null) throw new MSException("ENVIRONMENT_ASSET_BINDING_REQUIRED");
        var refs = new ArrayList<>(source == null ? List.<TestAssetRefDTO>of() : source);
        if (refs.stream().anyMatch(java.util.Objects::isNull)) throw new MSException("ASSET_REFERENCE_INVALID");
        var environments = refs.stream()
                .filter(ref -> "ENVIRONMENT".equalsIgnoreCase(StringUtils.trim(ref.getAssetType()))).toList();
        if (environments.size() > 1 || (!environments.isEmpty()
                && !assetId.equals(StringUtils.trim(environments.getFirst().getAssetId())))) {
            throw new MSException("ENVIRONMENT_ASSET_BINDING_CONFLICT");
        }
        if (environments.isEmpty()) {
            var ref = new TestAssetRefDTO(); ref.setAssetType("ENVIRONMENT"); ref.setAssetId(assetId);
            refs.add(ref);
        }
        return refs;
    }
}
