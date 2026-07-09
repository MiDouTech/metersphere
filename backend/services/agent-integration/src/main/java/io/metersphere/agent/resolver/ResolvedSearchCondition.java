package io.metersphere.agent.resolver;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class ResolvedSearchCondition {
    private Set<String> moduleIds = new LinkedHashSet<>();
    private String keyword;
    private List<String> priorities = new ArrayList<>();
    private List<String> lastExecuteResults = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<String> matchedBy = new ArrayList<>();
    private List<String> matchedModules = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
