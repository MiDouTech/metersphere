package io.metersphere.agent.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

class AgentServiceDependencyCycleTests {
    private static final String SERVICE_PACKAGE = "io.metersphere.agent.service";

    @Test
    void agentServiceDependenciesAreAcyclic() throws Exception {
        Set<Class<?>> serviceTypes = discoverServiceTypes();
        Map<Class<?>, Set<Class<?>>> dependencies = dependencyGraph(serviceTypes);

        List<Class<?>> cycle = findCycle(dependencies);
        if (!cycle.isEmpty()) {
            fail("Agent service dependency cycle: " + cycle.stream()
                    .map(Class::getSimpleName)
                    .reduce((left, right) -> left + " -> " + right)
                    .orElse("unknown"));
        }
    }

    private Set<Class<?>> discoverServiceTypes() throws IOException, URISyntaxException, ClassNotFoundException {
        Path classesRoot = Path.of(AgentRunnerService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path serviceRoot = classesRoot.resolve(SERVICE_PACKAGE.replace('.', '/'));
        Set<Class<?>> types = new HashSet<>();
        try (var paths = Files.walk(serviceRoot)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".class"))
                    .filter(value -> !value.getFileName().toString().contains("$")).toList()) {
                String relative = classesRoot.relativize(path).toString()
                        .replace(File.separatorChar, '.');
                Class<?> type = Class.forName(relative.substring(0, relative.length() - ".class".length()));
                if (type.isAnnotationPresent(Service.class)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    private Map<Class<?>, Set<Class<?>>> dependencyGraph(Set<Class<?>> serviceTypes) {
        Map<Class<?>, Set<Class<?>>> graph = new HashMap<>();
        for (Class<?> serviceType : serviceTypes) {
            Set<Class<?>> dependencies = new HashSet<>();
            for (Field field : serviceType.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && (field.isAnnotationPresent(Resource.class) || field.isAnnotationPresent(Autowired.class))
                        && serviceTypes.contains(field.getType())) {
                    dependencies.add(field.getType());
                }
            }
            Constructor<?>[] constructors = serviceType.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructors.length == 1 || constructor.isAnnotationPresent(Autowired.class)) {
                    for (Class<?> parameterType : constructor.getParameterTypes()) {
                        if (serviceTypes.contains(parameterType)) {
                            dependencies.add(parameterType);
                        }
                    }
                }
            }
            graph.put(serviceType, dependencies);
        }
        return graph;
    }

    private List<Class<?>> findCycle(Map<Class<?>, Set<Class<?>>> graph) {
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> active = new HashSet<>();
        Deque<Class<?>> path = new ArrayDeque<>();
        for (Class<?> type : graph.keySet()) {
            List<Class<?>> cycle = visit(type, graph, visited, active, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private List<Class<?>> visit(Class<?> type, Map<Class<?>, Set<Class<?>>> graph,
                                 Set<Class<?>> visited, Set<Class<?>> active, Deque<Class<?>> path) {
        if (active.contains(type)) {
            List<Class<?>> cycle = new ArrayList<>();
            boolean include = false;
            for (Class<?> item : path) {
                include = include || item.equals(type);
                if (include) {
                    cycle.add(item);
                }
            }
            cycle.add(type);
            return cycle;
        }
        if (!visited.add(type)) {
            return List.of();
        }
        active.add(type);
        path.addLast(type);
        for (Class<?> dependency : graph.getOrDefault(type, Set.of())) {
            List<Class<?>> cycle = visit(dependency, graph, visited, active, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.removeLast();
        active.remove(type);
        return List.of();
    }
}
