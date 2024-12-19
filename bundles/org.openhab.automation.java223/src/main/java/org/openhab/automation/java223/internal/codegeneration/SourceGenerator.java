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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.common.ThreadPoolManager;
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
 * The SourceGenerator is responsible for generating the additional classes
 * helping for rule development. It uses freemarker as a template engine.
 * Include a delayed mechanism to prevent creating file multiple time when there is many
 * modifications in the registry (especially useful at startup)
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class SourceGenerator {

    private static final String GENERATED = "generated";

    private final Logger logger = LoggerFactory.getLogger(SourceGenerator.class);

    private final ItemRegistry itemRegistry;
    private final ThingRegistry thingRegistry;
    private final BundleContext bundleContext;

    private final SourceWriter sourceWriter;
    private final DependencyGenerator dependencyGenerator;

    private static final String TPL_LOCATION = "/generated/";

    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);

    private Integer stabilityGenerationWaitTime;

    // keep a reference to generation method, to use as a key in the delayed map
    private final InternalGenerator actionGeneration = this::internalGenerateActions;
    private final InternalGenerator itemGeneration = this::internalGenerateItems;
    private final InternalGenerator thingGeneration = this::internalGenerateThings;
    private final Map<InternalGenerator, ScheduledFuture<?>> futureGeneration = new HashMap<>();

    private final ScheduledExecutorService scheduledPool = ThreadPoolManager
            .getScheduledPool(ThreadPoolManager.THREAD_POOL_NAME_COMMON);

    /**
     *
     * @param sourceWriter The real writer, with a cache to avoid writing something already existing
     * @param dependencyGenerator We will add to the dependency generator a list of package we think would be
     *            interesting to export
     * @param itemRegistry Lookup inside the itemRegistry to generate a list of item
     * @param thingRegistry Lookup inside the thingRegistry to generate a list of thing
     * @param bundleContext We will search for class action inside
     * @param stabilityGenerationWaitTime Wait until no new call are made during the period defined
     */
    public SourceGenerator(SourceWriter sourceWriter, DependencyGenerator dependencyGenerator,
            ItemRegistry itemRegistry, ThingRegistry thingRegistry, BundleContext bundleContext,
            Integer stabilityGenerationWaitTime) {
        this.sourceWriter = sourceWriter;
        this.itemRegistry = itemRegistry;
        this.thingRegistry = thingRegistry;
        this.bundleContext = bundleContext;
        this.dependencyGenerator = dependencyGenerator;
        this.stabilityGenerationWaitTime = stabilityGenerationWaitTime;

        cfg.setClassForTemplateLoading(SourceGenerator.class, "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /**
     * Delaying generation is especially useful during startup, when item and thing are not all properly initialized.
     * To avoid overwriting file with incomplete list of things/items/actions, we must avoid writing to the file if the
     * registries are not completely ready.
     * Until there is no more item/thing/action activating, this code will delay code generation.
     *
     * @param generator The generator responsible for creating the class
     */
    private synchronized void delayWhenStable(InternalGenerator generator) {
        ScheduledFuture<?> scheduledFuture = futureGeneration.get(generator);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        Runnable command = () -> {
            try {
                generator.generate();
                futureGeneration.remove(generator);
            } catch (IOException | TemplateException e) {
                logger.warn("Cannot create helper class file in library directory", e);
            }
        };
        futureGeneration.put(generator,
                scheduledPool.schedule(command, stabilityGenerationWaitTime, TimeUnit.MILLISECONDS));
    }

    public void generateActions() {
        delayWhenStable(actionGeneration);
    }

    public void generateItems() {
        delayWhenStable(itemGeneration);
    }

    public void generateThings() {
        delayWhenStable(thingGeneration);
    }

    public void generateJava223Script() {
        String packageName = sourceWriter.getPackageName(GENERATED);

        try {
            Template template = cfg.getTemplate(TPL_LOCATION + "Java223Script.ftl");
            Map<String, Object> context = new HashMap<>();
            context.put("packageName", packageName);

            StringWriter writer = new StringWriter();
            template.process(context, writer);

            sourceWriter.replaceHelperFileIfNotEqual(packageName, "Java223Script", writer.toString());
        } catch (IOException | TemplateException e) {
            logger.warn("Cannot create helper class file in library directory", e);
        }
    }

    @SuppressWarnings({ "unused", "null" })
    private void internalGenerateActions() throws IOException, TemplateException {
        List<ThingActions> thingActions;
        try {
            Set<Class<?>> classes = new HashSet<>();
            thingActions = bundleContext.getServiceReferences(ThingActions.class, null).stream()
                    .map(bundleContext::getService).filter(sr -> classes.add(sr.getClass())).toList();
        } catch (InvalidSyntaxException e) {
            logger.warn("Failed to get thing actions: {}", e.getMessage());
            return;
        }

        Template templateAction = cfg.getTemplate(TPL_LOCATION + "ThingAction.ftl");
        Map<String, Set<String>> actionsByScope = new HashMap<>();
        Set<String> allClassesToImport = new HashSet<>();

        for (ThingActions thingAction : thingActions) {
            Class<? extends ThingActions> clazz = thingAction.getClass();

            ThingActionsScope scopeAnnotation = clazz.getAnnotation(ThingActionsScope.class);
            if (scopeAnnotation == null) {
                continue;
            }
            String scope = scopeAnnotation.name();
            String simpleClassName = clazz.getSimpleName();
            String packageName = sourceWriter.getPackageName(GENERATED, scope);
            actionsByScope.computeIfAbsent(scope, (key -> new HashSet<>())).add(packageName + "." + simpleClassName);

            logger.trace("Processing class '{}' in package '{}'", simpleClassName, clazz.getPackageName());

            List<Method> methods = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.getDeclaredAnnotation(RuleAction.class) != null).toList();

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

            sourceWriter.replaceHelperFileIfNotEqual(packageName, simpleClassName, writer.toString());
        }

        // adding classes to the lib of exported dependencies
        dependencyGenerator.setClassesToAddToDependenciesLib(allClassesToImport);

        // now generate action factory :
        Template templateActionFactory = cfg.getTemplate(TPL_LOCATION + "Actions.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", sourceWriter.getPackageName(GENERATED));
        context.put("classesToImport", actionsByScope.values().stream().flatMap(Collection::stream).toList());
        @SuppressWarnings("unchecked")
        TemplateMethodModelEx tmmLastName = (
                args) -> className(((List<freemarker.template.SimpleScalar>) args).get(0).getAsString()).get();
        context.put("lastName", tmmLastName);
        @SuppressWarnings("unchecked")
        TemplateMethodModelEx tmmCapitalize = (args) -> capitalize(
                ((List<freemarker.template.SimpleScalar>) args).get(0).getAsString());
        context.put("camelCase", tmmCapitalize);
        context.put("actionsByScope", actionsByScope);
        StringWriter writer = new StringWriter();
        templateActionFactory.process(context, writer);

        sourceWriter.replaceHelperFileIfNotEqual(sourceWriter.getPackageName(GENERATED), "Actions", writer.toString());
    }

    /**
     * Return a user-friendly short readable name (without package details) and add the relevant full class name to the
     * imports list
     *
     * @param type The type to consider
     * @param imports A set to add imports into
     * @return a user-friendly printable name (without package)
     */
    protected static String parseArgumentType(Type type, Set<String> imports) {
        String typeName = type.getTypeName();
        String currentName = "";
        StringBuilder friendlyFullType = new StringBuilder();
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
                        friendlyFullType.append(classNameOfAPackage.get());
                    } else {
                        friendlyFullType.append(currentName);
                    }
                }
                if (!isAOKClassCharacter) {
                    friendlyFullType.append(ch);
                }
                currentName = "";
            }
        }
        return friendlyFullType.toString();
    }

    private void internalGenerateItems() throws IOException, TemplateException {
        Collection<Item> items = itemRegistry.getItems();
        String packageName = sourceWriter.getPackageName(GENERATED);

        Template template = cfg.getTemplate(TPL_LOCATION + "Items.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);
        context.put("items", items);
        context.put("itemImports",
                items.stream().map(item -> item.getClass().getCanonicalName()).collect(Collectors.toSet()));

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        sourceWriter.replaceHelperFileIfNotEqual(packageName, "Items", writer.toString());
    }

    private void internalGenerateThings() throws IOException, TemplateException {
        Collection<Thing> things = thingRegistry.getAll();
        String packageName = sourceWriter.getPackageName(GENERATED);

        Template template = cfg.getTemplate(TPL_LOCATION + "Things.ftl");
        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);

        context.put("things", things);

        @SuppressWarnings("unchecked")
        TemplateMethodModelEx tmm = (args) -> escapeName(
                ((List<freemarker.ext.beans.StringModel>) args).get(0).getWrappedObject().toString());
        context.put("escapeName", tmm);

        StringWriter writer = new StringWriter();
        template.process(context, writer);

        sourceWriter.replaceHelperFileIfNotEqual(packageName, "Things", writer.toString());
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

    public record MethodDTO(String returnValueType, String name, List<String> parameterTypes) {
    }

    private interface InternalGenerator {
        void generate() throws IOException, TemplateException;
    }

    public void setStabilityGenerationWaitTime(Integer stabilityGenerationWaitTime) {
        this.stabilityGenerationWaitTime = stabilityGenerationWaitTime;
    }
}
