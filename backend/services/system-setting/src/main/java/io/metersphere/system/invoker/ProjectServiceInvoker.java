package io.metersphere.system.invoker;

import io.metersphere.system.service.CleanupProjectResourceService;
import io.metersphere.system.service.CreateProjectResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ProjectServiceInvoker {
    private final ObjectProvider<CleanupProjectResourceService> cleanupProjectResourceServices;

    private final ObjectProvider<CreateProjectResourceService> createProjectResourceServices;


    @Autowired
    public ProjectServiceInvoker(ObjectProvider<CleanupProjectResourceService> services,
                                 ObjectProvider<CreateProjectResourceService> createProjectResourceServices) {
        this.cleanupProjectResourceServices = services;
        this.createProjectResourceServices = createProjectResourceServices;
    }

    public void invokeServices(String projectId) {
        cleanupProjectResourceServices.orderedStream()
                .forEach(service -> service.deleteResources(projectId));
    }

    public void invokeCreateServices(String projectId) {
        createProjectResourceServices.orderedStream()
                .forEach(service -> service.createResources(projectId));
    }
}
