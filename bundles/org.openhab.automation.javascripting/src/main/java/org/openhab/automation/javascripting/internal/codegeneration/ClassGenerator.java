/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.automation.javascripting.internal.codegeneration;

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
import org.openhab.automation.javascripting.internal.JavaScriptingConstants;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
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
 * @author Gwendal Roulleau - Refactor using velocity
 */
@NonNullByDefault
public class ClassGenerator {

    public static final String HELPER_PACKAGE = "helper";

    private final Logger logger = LoggerFactory.getLogger(ClassGenerator.class);

    private final Map<String, String> scopeClasses = new HashMap<>();

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

        String helperPackageFolderS = HELPER_PACKAGE.replaceAll("\\.", "/");
        Path scopeJavaFile = folder.resolve(helperPackageFolderS);
        Files.createDirectories(scopeJavaFile);

        cfg.setClassForTemplateLoading(ClassGenerator.class, "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

    }

    public boolean generateThingActions() throws IOException, TemplateException {
        List<ThingActions> thingActions;
        try {
            Set<Class<?>> classes = new HashSet<>();
            thingActions = bundleContext.getServiceReferences(ThingActions.class, null).stream()
                    .map(bundleContext::getService).filter(sr -> classes.add(sr.getClass()))
                    .collect(Collectors.toList());
        } catch (InvalidSyntaxException e) {
            logger.warn("Failed to get thing actions: {}", e.getMessage());
            return false;
        }

        Set<String> scopes = new HashSet<>();

        boolean changed = false;

        Template template = cfg.getTemplate("/ThingAction.ftl");

        for (ThingActions thingAction : thingActions) {
            Class<? extends ThingActions> clazz = thingAction.getClass();

            String packageName = clazz.getPackageName();

            String scope = clazz.getAnnotation(ThingActionsScope.class).name().toString();
            if (scope == null) {
                logger.warn("Found ThingActions class '{}' but no scope, ignoring", clazz.getName());
                continue;
            }

            String simpleClassName = scope.substring(0, 1).toUpperCase() + scope.substring(1, scope.length())
                    + clazz.getSimpleName();

            logger.trace("Processing class '{}' in package '{}'", simpleClassName, packageName);

            List<Method> methods = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.getDeclaredAnnotation(RuleAction.class) != null)
                    .collect(Collectors.toList());

            Set<String> classesToImport = new HashSet<>();
            List<MethodDTO> methodsDTO = new ArrayList<>();

            for (Method method : methods) {
                String name = method.getName();
                String returnValue = method.getGenericReturnType().getTypeName();

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
            context.put("packageName", HELPER_PACKAGE);
            context.put("scope", scope);
            context.put("classesToImport", classesToImport);
            context.put("simpleClassName", simpleClassName);
            context.put("methods", methodsDTO);

            StringWriter writer = new StringWriter();
            template.process(context, writer);

            if (replaceIfNotEqual(simpleClassName, writer.toString())) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean replaceIfNotEqual(String className, String generatedClass) throws IOException {
        String key = HELPER_PACKAGE + "." + className;
        if (!generatedClass.equals(scopeClasses.put(key, generatedClass))) {
            writeHelperFile(className, generatedClass);
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
                ? Objects.requireNonNull(((GenericArrayType) type).getGenericComponentType()).getTypeName() + "[]"
                : type.getTypeName();
    }

    public void generateItems() throws IOException, TemplateException {
        Collection<Item> items = itemRegistry.getItems();

        Template template = cfg.getTemplate("/Items.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("HELPER_PACKAGE", HELPER_PACKAGE);
        context.put("items", items);

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        writeHelperFile("Items", writer.toString());
    }

    public void generateThings() throws IOException, TemplateException {
        Collection<Thing> things = thingRegistry.getAll();

        Template template = cfg.getTemplate("/Things.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("HELPER_PACKAGE", HELPER_PACKAGE);

        context.put("things", things);
        TemplateMethodModelEx tmm = (args) -> escapeName(args);
        context.put("escapeName", tmm);

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        writeHelperFile("Things", writer.toString());
    }

    private void writeHelperFile(String className, String generatedClass) throws IOException {
        String packageFolder = HELPER_PACKAGE.replaceAll("\\.", "/");
        Path javaFile = folder.resolve(packageFolder + "/" + className + "." + JavaScriptingConstants.JAVA_FILE_TYPE);

        Files.createDirectories(javaFile.getParent());
        try (FileOutputStream outFile = new FileOutputStream(javaFile.toFile())) {
            outFile.write(generatedClass.getBytes(StandardCharsets.UTF_8));
            logger.debug("Wrote generated class: {}", javaFile.toAbsolutePath());
        }
    }

    private String escapeName(List<freemarker.ext.beans.StringModel> textToEscape) {
        return ((UID) (textToEscape.get(0).getWrappedObject())).toString().replace(":", "_").replace("-", "_");
    }
}
