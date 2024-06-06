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
package org.openhab.automation.java223.internal;

import static org.openhab.automation.java223.common.Java223Constants.LIB_DIR;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.script.ScriptEngine;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.Java223Constants;
import org.openhab.automation.java223.internal.codegeneration.ClassGenerator;
import org.openhab.automation.java223.internal.codegeneration.DependencyGenerator;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;
import org.openhab.automation.java223.internal.strategy.ScriptWrappingStategy;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.module.script.ScriptEngineFactory;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.items.events.ItemAddedEvent;
import org.openhab.core.items.events.ItemRemovedEvent;
import org.openhab.core.service.WatchService;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.events.ThingAddedEvent;
import org.openhab.core.thing.events.ThingRemovedEvent;
import org.openhab.core.thing.events.ThingStatusInfoChangedEvent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.JavaScriptEngine;
import ch.obermuhlner.scriptengine.java.JavaScriptEngineFactory;
import ch.obermuhlner.scriptengine.java.compilation.ScriptInterceptorStrategy;
import ch.obermuhlner.scriptengine.java.packagelisting.PackageResourceListingStrategy;
import freemarker.template.TemplateException;

/**
 * This is an implementation of a {@link ScriptEngineFactory} for Java
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@Component(service = { ScriptEngineFactory.class, Java223ScriptEngineFactory.class, EventSubscriber.class })
public class Java223ScriptEngineFactory extends JavaScriptEngineFactory
        implements ScriptEngineFactory, EventSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(Java223ScriptEngineFactory.class);

    private BundleWiring bundleWiring;
    private BundleContext bundleContext;

    private PackageResourceListingStrategy osgiPackageResourceListingStrategy;
    private Java223Strategy java223Strategy;
    private ScriptInterceptorStrategy scriptWrappingStrategy;

    private final WatchService watchService;

    @Nullable
    private ClassGenerator classGenerator;

    @Nullable
    private JavaScriptEngine scriptEngineInstance;

    private static final boolean REUSE_SCRIPT_ENGINE = true;

    private static final Set<ThingStatus> INITIALIZED = Set.of(ThingStatus.ONLINE, ThingStatus.OFFLINE,
            ThingStatus.UNKNOWN);
    private static final Set<String> ACTION_EVENTS = Set.of(ThingStatusInfoChangedEvent.TYPE);
    private static final Set<String> ITEM_EVENTS = Set.of(ItemAddedEvent.TYPE, ItemRemovedEvent.TYPE);
    private static final Set<String> THING_EVENTS = Set.of(ThingAddedEvent.TYPE, ThingRemovedEvent.TYPE);
    private static final Set<String> EVENTS = Stream.of(ACTION_EVENTS, ITEM_EVENTS, THING_EVENTS).flatMap(Set::stream)
            .collect(Collectors.toSet());

    @Activate
    public Java223ScriptEngineFactory(BundleContext bundleContext, Map<String, Object> properties,
            @Reference(target = WatchService.CONFIG_WATCHER_FILTER) WatchService watchService,
            @Reference ItemRegistry itemRegistry, @Reference ThingRegistry thingRegistry) {

        try {
            Files.createDirectories(LIB_DIR);
        } catch (IOException e) {
            logger.warn("Failed to create directory '{}': {}", LIB_DIR, e.getMessage());
            throw new IllegalStateException("Failed to initialize lib folder.");
        }

        this.bundleContext = bundleContext;
        this.bundleWiring = bundleContext.getBundle().adapt(BundleWiring.class);

        String additionalBundlesConfig = (String) properties.getOrDefault("additionalBundles", "");

        osgiPackageResourceListingStrategy = new PackageResourceListingStrategy() {
            @Override
            public Collection<String> listResources(String packageName) {
                return listClassResources(packageName);
            }
        };
        java223Strategy = new Java223Strategy(getAdditionalBindings());
        scriptWrappingStrategy = new ScriptWrappingStategy();

        try {
            ClassGenerator localClassGenerator = new ClassGenerator(LIB_DIR, itemRegistry, thingRegistry,
                    bundleContext);
            this.classGenerator = localClassGenerator;
            generateActions();
            generateThings();
            generateItems();
            watchService.registerListener(localClassGenerator, LIB_DIR);
            DependencyGenerator.createCoreDependencies(LIB_DIR, additionalBundlesConfig, bundleContext);
        } catch (IOException | TemplateException e) {
            logger.error("Cannot create helper class file in library dir. " + e.getMessage());
        }

        this.watchService = watchService;
        java223Strategy.scanLibDirectory();
        watchService.registerListener(java223Strategy, LIB_DIR);

        logger.info("Bundle activated");
    }

    @Deactivate
    public void deactivate() {
        watchService.unregisterListener(java223Strategy);
    }

    @Override
    public List<String> getScriptTypes() {
        String[] types = { Java223Constants.JAVA_FILE_TYPE };
        return Arrays.asList(types);
    }

    @Override
    public void scopeValues(ScriptEngine scriptEngine, Map<String, Object> scopeValues) {
        for (Entry<String, Object> entry : scopeValues.entrySet()) {
            scriptEngine.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public @Nullable ScriptEngine createScriptEngine(String scriptType) {
        if (getScriptTypes().contains(scriptType)) {

            if (this.scriptEngineInstance == null || !REUSE_SCRIPT_ENGINE) {
                JavaScriptEngine engine = (JavaScriptEngine) getScriptEngine();

                engine.setExecutionStrategyFactory(java223Strategy);
                engine.setBindingStrategy(java223Strategy);
                engine.setPackageResourceListingStrategy(osgiPackageResourceListingStrategy);
                engine.setCompilationStrategy(java223Strategy);
                engine.setScriptInterceptorStrategy(scriptWrappingStrategy);

                engine.setCompilationOptions(Arrays.asList("-g"));

                this.scriptEngineInstance = engine;
                return engine;
            } else {
                return scriptEngineInstance;
            }
        }
        return null;
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new JavaScriptEngine();
    }

    private Map<String, Object> getAdditionalBindings() {
        RuleManager ruleManager = bundleContext.getService(bundleContext.getServiceReference(RuleManager.class));
        MetadataRegistry metadataRegistry = bundleContext
                .getService(bundleContext.getServiceReference(MetadataRegistry.class));
        return Map.of(Java223Constants.RULE_MANAGER, ruleManager, Java223Constants.METADATA_REGISTRY, metadataRegistry);
    }

    private Collection<String> listClassResources(String packageName) {
        String path = packageName.replace(".", "/");
        path = "/" + path;

        Collection<String> resources = bundleWiring.listResources(path, "*.class", 0);

        return resources;
    }

    @Override
    public @NonNull Set<@NonNull String> getSubscribedEventTypes() {
        return EVENTS;
    }

    @Override
    public void receive(Event event) {
        String eventType = event.getType();

        if (ACTION_EVENTS.contains(eventType)) {
            ThingStatusInfoChangedEvent eventStatusInfoChange = (ThingStatusInfoChangedEvent) event;
            if ((ThingStatus.INITIALIZING.equals(eventStatusInfoChange.getOldStatusInfo().getStatus())
                    && INITIALIZED.contains(eventStatusInfoChange.getStatusInfo().getStatus()))
                    || (ThingStatus.UNINITIALIZED.equals(eventStatusInfoChange.getStatusInfo().getStatus())
                            && INITIALIZED.contains(eventStatusInfoChange.getOldStatusInfo().getStatus()))) {
                try {
                    generateActions();
                } catch (IOException | TemplateException e) {
                    logger.warn("Failed to (re-)build thing action classes", e);
                }
            }
        } else if (ITEM_EVENTS.contains(eventType)) {
            logger.debug("Added/updated item: {}", event);
            try {
                generateItems();
            } catch (IOException | TemplateException e) {
                logger.warn("Failed to (re-)build item class", e);
            }
        } else if (THING_EVENTS.contains(eventType)) {
            logger.debug("Added/updated thing: {}", event);
            try {
                generateThings();
            } catch (IOException | TemplateException e) {
                logger.warn("Failed to (re-)build thing class", e);
            }
        }
    }

    private void generateItems() throws IOException, TemplateException {
        ClassGenerator localClassGenerator = classGenerator;
        if (localClassGenerator == null) {
            logger.error("Cannot use the class generator, initialization error");
            return;
        }
        localClassGenerator.generateItems();
    }

    private void generateThings() throws IOException, TemplateException {
        ClassGenerator localClassGenerator = classGenerator;
        if (localClassGenerator == null) {
            logger.error("Cannot use the class generator, initialization error");
            return;
        }
        localClassGenerator.generateThings();
    }

    private void generateActions() throws IOException, TemplateException {
        ClassGenerator localClassGenerator = classGenerator;
        if (localClassGenerator == null) {
            logger.error("Cannot use the class generator, initialization error");
            return;
        }
        localClassGenerator.generateActions();
    }
}
