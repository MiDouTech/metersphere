package io.metersphere.app;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

class OwnershipExpressionContractTests {
    @org.junit.jupiter.api.Test
    void ownershipVariablesAndDirectGettersExist() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("backend/services"))) root = root.getParent();
        org.junit.jupiter.api.Assertions.assertNotNull(root, "Repository root must be present for source contract checks");
        List<String> violations = new ArrayList<>();
        List<Path> files;
        try (var walk = Files.walk(root.resolve("backend"))) {
            files = walk.filter(p -> p.toString().replace('\\','/').contains("/src/main/java/"))
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java")).toList();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        int checked=0, skipped=0;
        try (var manager = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            var task=(JavacTask)compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(files));
            for (var unit: task.parse()) for (var type: unit.getTypeDecls()) {
                if (!(type instanceof ClassTree tree)) continue;
                Class<?> controller;
                try { controller=Class.forName(unit.getPackageName()+"."+tree.getSimpleName(),false,OwnershipExpressionContractTests.class.getClassLoader()); }
                catch (LinkageError|ClassNotFoundException ex) { skipped++;continue; }
                Method[] methods;
                try { methods=controller.getDeclaredMethods(); } catch(LinkageError ex) { skipped++;continue; }
                for(var member:tree.getMembers()) if(member instanceof MethodTree method) {
                    for(var annotation:method.getModifiers().getAnnotations()) {
                        String annotationName=annotation.getAnnotationType().toString();
                        if (!Set.of("CheckOwner","CheckProjectOwner","CheckOrganizationOwner").contains(annotationName)) continue;
                        String expression=null;
                        for(var arg:annotation.getArguments()) if(arg instanceof AssignmentTree assign && assign.getVariable().toString().equals("resourceId") && assign.getExpression() instanceof LiteralTree literal) expression=String.valueOf(literal.getValue());
                        if(expression==null) continue;
                        Method reflected=null;
                        for(var candidate:methods) if(candidate.getName().equals(method.getName().toString()) && candidate.getParameterCount()==method.getParameters().size()) {
                            boolean matches=true;
                            for(int index=0; index<candidate.getParameterCount(); index++) {
                                String raw=method.getParameters().get(index).getType().toString().replaceAll("<.*>", "");
                                raw=raw.substring(raw.lastIndexOf('.')+1);
                                if(!raw.equals(candidate.getParameterTypes()[index].getSimpleName())) { matches=false;break; }
                            }
                            if(matches) { reflected=candidate;break; }
                        }
                        if(reflected==null) { skipped++;continue; }
                        var roots=Pattern.compile("#(\\w+)").matcher(expression);
                        while(roots.find()) {
                            boolean found=false;
                            for(var parameter:method.getParameters()) if(parameter.getName().contentEquals(roots.group(1))) found=true;
                            if(!found && !Set.of("root","this").contains(roots.group(1))) {
                                violations.add(controller.getName()+"."+method.getName()+" -> UNKNOWN PARAMETER #"+roots.group(1));
                            }
                        }
                        var matcher=Pattern.compile("#(\\w+)\\.(get\\w+)\\(\\s*\\)").matcher(expression);
                        while(matcher.find()) {
                            for(int i=0;i<method.getParameters().size();i++) if(method.getParameters().get(i).getName().contentEquals(matcher.group(1))) {
                                var parameter=reflected.getParameterTypes()[i];checked++;
                                try { parameter.getMethod(matcher.group(2)); }
                                catch(NoSuchMethodException ex) { violations.add(controller.getName()+"."+method.getName()+" -> "+parameter.getName()+"."+matcher.group(2)+"()"); }
                            }
                        }
                    }
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(checked > 300, "Controller scan must not silently lose coverage");
        org.junit.jupiter.api.Assertions.assertEquals(0, skipped, "All controller classes and overloads must resolve");
        org.junit.jupiter.api.Assertions.assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }
}
