/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.automation.java223.internal.codegeneration;

import static org.openhab.automation.java223.common.Java223Constants.LIB_DIR;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.automation.java223.common.Java223Constants;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.service.WatchService;
import org.openhab.core.service.WatchService.Kind;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.UID;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateMethodModelEx;

/**
 * The {@link ClassGenerator} is responsible for generating the additional classes for rule development
 *
 * @author Jan N. Klug - Initial contribution
 * @author Gwendal Roulleau - Refactor using freemarker
 */
@NonNullByDefault
public class ClassGenerator implements WatchService.WatchEventListener {

    public static final String HELPER_PACKAGE = "helper";

    private final Logger logger = LoggerFactory.getLogger(ClassGenerator.class);

    private final Map<String, String> generatedClasses = new HashMap<>();

    private final Path folder;
    private final ItemRegistry itemRegistry;
    private final ThingRegistry thingRegistry;
    private final BundleContext bundleContext;

    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);

    public ClassGenerator(Path folder, ItemRegistry itemRegistry, ThingRegistry thingRegistry,
            BundleContext bundleContext) throws IOException {
        this.folder = folder;
        this.itemRegistry = itemRegistry;
        this.thingRegistry = thingRegistry;
        this.bundleContext = bundleContext;

        String helperPackageFolder = HELPER_PACKAGE.replaceAll("\\.", "/");
        Files.createDirectories(folder.resolve(helperPackageFolder));

        cfg.setClassForTemplateLoading(ClassGenerator.class, "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    private String lastName(String fullName) {
        int lastDotIndex = fullName.lastIndexOf('.');
        return lastDotIndex == -1 ? fullName : fullName.substring(lastDotIndex + 1);
    }

    private boolean replaceIfNotEqual(String packageName, String className, String generatedClass) throws IOException {
        String key = packageName + "." + className;
        if (!generatedClass.equals(generatedClasses.put(key, generatedClass))) {
            writeHelperFile(packageName, className, generatedClass);
            return true;
        } else {
            logger.debug("{} has not changed.", key);
            return false;
        }
    }

    private String classToImport(Class<?> clazz) {
        if ((clazz.isPrimitive())
                || (clazz.isArray() && Objects.requireNonNull(clazz.getComponentType()).isPrimitive())) {
            return "";
        }
        if (clazz.isArray()) {
            return Objects.requireNonNull(clazz.getComponentType()).getName();
        } else {
            return clazz.getName();
        }
    }

    private String typeToParameter(Type type) {
        return type instanceof GenericArrayType
                ? lastName(Objects.requireNonNull(((GenericArrayType) type).getGenericComponentType()).getTypeName())
                        + "[]"
                : lastName(type.getTypeName());
    }

    public void generateActions() throws IOException, TemplateException {
        List<ThingActions> thingActions;
        try {
            Set<Class<?>> classes = new HashSet<>();
            thingActions = bundleContext.getServiceReferences(ThingActions.class, null).stream()
                    .map(bundleContext::getService).filter(sr -> classes.add(sr.getClass()))
                    .collect(Collectors.toList());
        } catch (InvalidSyntaxException e) {
            logger.warn("Failed to get thing actions: {}", e.getMessage());
            return;
        }

        Template template = cfg.getTemplate("/ThingAction.ftl");

        for (ThingActions thingAction : thingActions) {
            Class<? extends ThingActions> clazz = thingAction.getClass();

            ThingActionsScope scopeAnnotation = clazz.getAnnotation(ThingActionsScope.class);
            if (scopeAnnotation == null) {
                continue;
            }
            String scope = scopeAnnotation.name().toString();
            String simpleClassName = clazz.getSimpleName();
            String packageName = getPackageName("generated", "action", scope);

            logger.trace("Processing class '{}' in package '{}'", simpleClassName, clazz.getPackageName());

            List<Method> methods = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.getDeclaredAnnotation(RuleAction.class) != null)
                    .collect(Collectors.toList());

            Set<String> classesToImport = new HashSet<>();
            List<MethodDTO> methodsDTO = new ArrayList<>();

            for (Method method : methods) {
                String name = method.getName();
                String returnValue = lastName(method.getGenericReturnType().getTypeName());

                classesToImport.addAll(
                        Arrays.asList(method.getParameterTypes()).stream().map(pt -> classToImport(pt)).toList());
                classesToImport.add(classToImport(method.getReturnType()));

                List<String> parametersType = Arrays.stream(method.getGenericParameterTypes())
                        .map(this::typeToParameter).collect(Collectors.toList());

                MethodDTO methodDTO = new MethodDTO(returnValue, name, parametersType);
                logger.trace("Found method '{}' with parameters '{}' and return value '{}'.", name, parametersType,
                        returnValue);

                methodsDTO.add(methodDTO);
            }

            Map<String, Object> context = new HashMap<>();
            context.put("packageName", packageName);
            context.put("scope", scope);
            context.put("classesToImport", classesToImport);
            context.put("simpleClassName", simpleClassName);
            context.put("methods", methodsDTO);

            StringWriter writer = new StringWriter();
            template.process(context, writer);

            replaceIfNotEqual(packageName, simpleClassName, writer.toString());
        }
    }

    public void generateItems() throws IOException, TemplateException {
        Collection<Item> items = itemRegistry.getItems();
        String packageName = getPackageName("generated");

        Template template = cfg.getTemplate("/Items.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);
        context.put("items", items);
        context.put("itemImports",
                items.stream().map(item -> item.getClass().getCanonicalName()).collect(Collectors.toSet()));

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        replaceIfNotEqual(packageName, "Items", writer.toString());
    }

    public void generateThings() throws IOException, TemplateException {
        Collection<Thing> things = thingRegistry.getAll();
        String packageName = getPackageName("generated");

        Template template = cfg.getTemplate("/Things.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);

        context.put("things", things);
        TemplateMethodModelEx tmm = (args) -> escapeName(args);
        context.put("escapeName", tmm);

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        replaceIfNotEqual(packageName, "Things", writer.toString());
    }

    private static String getPackageName(String... packagePath) {
        return HELPER_PACKAGE + "." + String.join(".", packagePath);
    }

    private void writeHelperFile(String packageName, String className, String generatedClass) throws IOException {
        String packageFolder = packageName.replaceAll("\\.", "/");
        Path javaFile = folder.resolve(packageFolder + "/" + className + "." + Java223Constants.JAVA_FILE_TYPE);

        Files.createDirectories(javaFile.getParent());
        try (FileOutputStream outFile = new FileOutputStream(javaFile.toFile())) {
            outFile.write(generatedClass.getBytes(StandardCharsets.UTF_8));
            logger.debug("Wrote generated class: {}", javaFile.toAbsolutePath());
        }
    }

    private String escapeName(List<freemarker.ext.beans.StringModel> textToEscape) {
        return ((UID) (textToEscape.get(0).getWrappedObject())).toString().replace(":", "_").replace("-", "_");
    }

    @Override
    public void processWatchEvent(Kind kind, Path path) {
        Path fullPath = LIB_DIR.resolve(path);
        if (fullPath.getFileName().toString().endsWith("." + Java223Constants.JAVA_FILE_TYPE)
                && (kind == Kind.DELETE || kind == Kind.MODIFY)) {
            // by intercepting delete or modify signal, we ensure that we remove java file from our internal database to
            // regenerate them thereafter
            String key = fullPath.toString().replace(File.separator, ".").substring(0, fullPath.toString().length());
            generatedClasses.remove(key);
        } else {
            logger.trace("Received '{}' for path '{}' - ignoring (wrong extension)", kind, fullPath);
        }
    }

    public static record MethodDTO(String returnValueType, String name, List<String> parameterTypes) {

    }
}
