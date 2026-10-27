package io.metersphere.functional.service;

import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class FunctionalExecutionViewService {
    private final List<FunctionalExecutionViewProvider> providers;
    public FunctionalExecutionViewService(List<FunctionalExecutionViewProvider> providers) { this.providers = providers; }

    public void enrich(FunctionalCaseDetailDTO source) {
        for (var provider : providers) {
            var view = provider.current(source);
            if (view.isPresent()) {
                source.setExecutionRef(Map.of("type", view.get().type(), "id", view.get().id()));
                source.setExecutionFacts(view.get().facts());
                return;
            }
        }
    }
}
