package io.metersphere.functional.service;

import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import java.util.Map;
import java.util.Optional;

/** Internal read projection; callers authorize the source case before asking for execution facts. */
public interface FunctionalExecutionViewProvider {
    Optional<View> current(FunctionalCaseDetailDTO source);

    record View(String type, String id, Map<String, Object> facts) { }
}
