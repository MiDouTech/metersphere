package io.metersphere.functional.hub.service;

import io.metersphere.sdk.exception.MSException;

/** A stale sync must roll back and retry from a new transaction after a detach. */
public class CaseAssetBindingChangedException extends MSException {
    public CaseAssetBindingChangedException() {
        super("CASE_SOURCE_BINDING_CHANGED：来源绑定已变化，请重新同步");
    }
}
