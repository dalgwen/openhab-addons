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

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
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
 * The {@link ClassGenerator} is responsible for generating the additional classes
 * helping for rule development
 *
 * @author Jan N. Klug - Initial contribution
 * @author Gwendal Roulleau - Refactor using freemarker
 */
@NonNullByDefault
public class ClassGenerator {

    private final Logger logger = LoggerFactory.getLogger(ClassGenerator.class);

    private final ItemRegistry itemRegistry;
    private final ThingRegistry thingRegistry;
    private final BundleContext bundleContext;

    private ClassWriter classWriter;
    private DependencyGenerator dependencyGenerator;

    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);

    public ClassGenerator(ClassWriter classWriter, DependencyGenerator dependencyGenerator, ItemRegistry itemRegistry,
            ThingRegistry thingRegistry, BundleContext bundleContext) {
        this.classWriter = classWriter;
        this.itemRegistry = itemRegistry;
        this.thingRegistry = thingRegistry;
        this.bundleContext = bundleContext;
        this.dependencyGenerator = dependencyGenerator;

        cfg.setClassForTemplateLoading(ClassGenerator.class, "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    @SuppressWarnings({ "unused", "null" })
    public synchronized void generateActions() throws IOException, TemplateException {
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

        Template templateAction = cfg.getTemplate("/helper/generated/ThingAction.ftl");
        Map<String, Set<String>> actionsByScope = new HashMap<>();
        Set<String> allClassesToImport = new HashSet<>();

        for (ThingActions thingAction : thingActions) {
            Class<? extends ThingActions> clazz = thingAction.getClass();

            ThingActionsScope scopeAnnotation = clazz.getAnnotation(ThingActionsScope.class);
            if (scopeAnnotation == null) {
                continue;
            }
            String scope = scopeAnnotation.name().toString();
            String simpleClassName = clazz.getSimpleName();
            String packageName = classWriter.getPackageName("generated", scope);
            actionsByScope.computeIfAbsent(scope, (key -> new HashSet<String>()))
                    .add(packageName + "." + simpleClassName);

            logger.trace("Processing class '{}' in package '{}'", simpleClassName, clazz.getPackageName());

            List<Method> methods = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.getDeclaredAnnotation(RuleAction.class) != null)
                    .collect(Collectors.toList());

            Set<String> classesToImport = new HashSet<>();
            List<MethodDTO> methodsDTO = new ArrayList<>();

            for (Method method : methods) {
                String name = method.getName();
                String returnValue = parseArgumentType(method.getGenericReturnType(), classesToImport);

                List<String> parametersType = Arrays.stream(method.getGenericParameterTypes())
                        .map(pt -> parseArgumentType(pt, classesToImport)).toList();

                MethodDTO methodDTO = new MethodDTO(returnValue, name, parametersType);
                logger.trace("Found method '{}' with parameters '{}' and return value '{}'.", name, parametersType,
                        returnValue);

                methodsDTO.add(methodDTO);
            }

            allClassesToImport.addAll(classesToImport);

            Map<String, Object> context = new HashMap<>();
            context.put("packageName", packageName);
            context.put("scope", scope);
            context.put("classesToImport", classesToImport);
            context.put("simpleClassName", simpleClassName);
            context.put("methods", methodsDTO);

            StringWriter writer = new StringWriter();
            templateAction.process(context, writer);

            classWriter.replaceHelperFileIfNotEqual(packageName, simpleClassName, writer.toString());
        }

        // adding classes to the lib of exported dependencies
        dependencyGenerator.setClassesToAddToDependenciesLib(allClassesToImport);

        // now generate action factory :
        Template templateActionFactory = cfg.getTemplate("/helper/generated/Actions.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", classWriter.getPackageName("generated"));
        context.put("classesToImport", actionsByScope.values().stream().flatMap(s -> s.stream()).toList());
        @SuppressWarnings("unchecked")
        TemplateMethodModelEx tmmLastName = (args) -> classNameSure(
                ((List<freemarker.template.SimpleScalar>) args).get(0).getAsString());
        context.put("lastName", tmmLastName);
        @SuppressWarnings("unchecked")
        TemplateMethodModelEx tmmCapitalize = (args) -> capitalize(
                ((List<freemarker.template.SimpleScalar>) args).get(0).getAsString());
        context.put("camelCase", tmmCapitalize);
        context.put("actionsByScope", actionsByScope);
        StringWriter writer = new StringWriter();
        templateActionFactory.process(context, writer);

        classWriter.replaceHelperFileIfNotEqual(classWriter.getPackageName("generated"), "Actions", writer.toString());
    }

    /**
     * Return a user friendly short readable name (without package details) and add the relevant full class name to the
     * imports
     * list
     *
     * @param type
     * @param imports A set to add imports into
     * @return a user friendly printable name (without package)
     */
    protected static String parseArgumentType(Type type, Set<String> imports) {
        String typeName = type.getTypeName();
        String currentName = "";
        String friendlyFullType = "";
        for (int i = 0; i < typeName.length(); i++) {
            char ch = typeName.charAt(i);
            boolean isAOKClassCharacter = Character.isLetter(ch) || Character.isDigit(ch)
                    || Character.valueOf(ch).equals('_') || Character.valueOf(ch).equals('.');
            if (isAOKClassCharacter) {
                currentName += ch;
            }
            if (!isAOKClassCharacter || i == typeName.length() - 1) {
                if (!currentName.isEmpty()) {
                    Optional<String> classNameOfAPackage = className(currentName);
                    if (classNameOfAPackage.isPresent()) {
                        imports.add(currentName);
                        friendlyFullType += classNameOfAPackage.get();
                    } else {
                        friendlyFullType += currentName;
                    }
                }
                if (!isAOKClassCharacter) {
                    friendlyFullType += ch;
                }
                currentName = "";
            }
        }
        return friendlyFullType;
    }

    public synchronized void generateItems() throws IOException, TemplateException {
        Collection<Item> items = itemRegistry.getItems();
        String packageName = classWriter.getPackageName("generated");

        Template template = cfg.getTemplate("/helper/generated/Items.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);
        context.put("items", items);
        context.put("itemImports",
                items.stream().map(item -> item.getClass().getCanonicalName()).collect(Collectors.toSet()));

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        classWriter.replaceHelperFileIfNotEqual(packageName, "Items", writer.toString());
    }

    public synchronized void generateThings() throws IOException, TemplateException {
        Collection<Thing> things = thingRegistry.getAll();
        String packageName = classWriter.getPackageName("generated");

        Template template = cfg.getTemplate("/helper/generated/Things.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);

        context.put("things", things);

        @SuppressWarnings("unchecked")
        TemplateMethodModelEx tmm = (args) -> escapeName(
                ((List<freemarker.ext.beans.StringModel>) args).get(0).getWrappedObject().toString());
        context.put("escapeName", tmm);

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        classWriter.replaceHelperFileIfNotEqual(packageName, "Things", writer.toString());
    }

    private static String capitalize(String minusString) {
        return minusString.substring(0, 1).toUpperCase() + minusString.substring(1);
    }

    private static String escapeName(String textToEscape) {
        return textToEscape.replace(":", "_").replace("-", "_");
    }

    private static Optional<String> className(String fullName) {
        int lastDotIndex = fullName.lastIndexOf('.');
        return lastDotIndex == -1 ? Optional.empty() : Optional.of(fullName.substring(lastDotIndex + 1));
    }

    private static String classNameSure(String fullName) {
        return className(fullName).get();
    }

    public static record MethodDTO(String returnValueType, String name, List<String> parameterTypes) {
    }
}
