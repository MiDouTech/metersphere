package io.metersphere.system.edit;

import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收集各模块注册的 ResourceEditAdapter
 */
@Component
public class ResourceEditAdapterRegistry {

    private final Map<String, ResourceEditAdapter> adapterMap = new HashMap<>();

    @Autowired(required = false)
    public void setAdapters(List<ResourceEditAdapter> adapters) {
        if (CollectionUtils.isEmpty(adapters)) {
            return;
        }
        for (ResourceEditAdapter adapter : adapters) {
            adapterMap.put(adapter.resourceType(), adapter);
        }
    }

    public ResourceEditAdapter get(String resourceType) {
        return adapterMap.get(resourceType);
    }
}
