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
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.automation.java223.internal.codegeneration.DependencyGenerator;
import org.openhab.automation.java223.internal.codegeneration.SourceGenerator;
import org.openhab.automation.java223.internal.codegeneration.SourceHelperCopier;
import org.openhab.automation.java223.internal.codegeneration.SourceWriter;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;
import org.openhab.automation.java223.internal.strategy.ScriptWrappingStrategy;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.module.script.ScriptEngineFactory;
import org.openhab.core.config.core.ConfigParser;
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
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.JavaScriptEngine;
import ch.obermuhlner.scriptengine.java.JavaScriptEngineFactory;
import ch.obermuhlner.scriptengine.java.compilation.ScriptInterceptorStrategy;
import ch.obermuhlner.scriptengine.java.packagelisting.PackageResourceListingStrategy;

/**
 * This is an implementation of a {@link ScriptEngineFactory} for Java
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@Component(service = { ScriptEngineFactory.class, Java223ScriptEngineFactory.class,
        EventSubscriber.class }, configurationPid = "automation.java223")
public class Java223ScriptEngineFactory extends JavaScriptEngineFactory
        implements ScriptEngineFactory, EventSubscriber {

    private static final Logger logger = LoggerFactory.getLogger(Java223ScriptEngineFactory.class);

    private BundleWiring bundleWiring;
    private BundleContext bundleContext;

    private PackageResourceListingStrategy osgiPackageResourceListingStrategy;
    private Java223Strategy java223Strategy;
    private ScriptInterceptorStrategy scriptWrappingStrategy;

    private final WatchService watchService;

    private SourceGenerator classGenerator;
    private DependencyGenerator dependencyGenerator;

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

        String additionalBundlesConfig = ConfigParser.valueAsOrElse(properties.get("additionalBundles"), String.class,
                "");
        String additionalClassesConfig = ConfigParser.valueAsOrElse(properties.get("additionalClasses"), String.class,
                "");
        Integer initializationWaitTime = ConfigParser.valueAsOrElse(properties.get("stabilityGenerationWaitTime"),
                Integer.class, 10000);

        osgiPackageResourceListingStrategy = new PackageResourceListingStrategy() {
            @Override
            public Collection<String> listResources(String packageName) {
                return listClassResources(packageName);
            }
        };
        java223Strategy = new Java223Strategy(getAdditionalBindings());
        scriptWrappingStrategy = new ScriptWrappingStrategy();

        try {
            dependencyGenerator = new DependencyGenerator(LIB_DIR, additionalBundlesConfig, additionalClassesConfig,
                    bundleContext);
            SourceWriter classWriter = new SourceWriter(LIB_DIR);
            SourceHelperCopier.copyFiles(classWriter);
            this.classGenerator = new SourceGenerator(classWriter, dependencyGenerator, itemRegistry, thingRegistry,
                    bundleContext, initializationWaitTime);
            classGenerator.generateThings();
            classGenerator.generateActions();
            classGenerator.generateItems();
            dependencyGenerator.createCoreDependencies();
            watchService.registerListener(classWriter, LIB_DIR);
        } catch (IOException e) {
            throw new Java223Exception("Cannot create helper class file in library directory", e);
        }

        this.watchService = watchService;
        java223Strategy.scanLibDirectory();
        watchService.registerListener(java223Strategy, LIB_DIR);

        logger.info("Bundle activated");
    }

    @Modified
    protected void modified(Map<String, Object> properties) {
        String additionalBundlesConfig = ConfigParser.valueAsOrElse(properties.get("additionalBundles"), String.class,
                "");
        String additionalClassesConfig = ConfigParser.valueAsOrElse(properties.get("additionalClasses"), String.class,
                "");
        dependencyGenerator.setAdditionalConfig(additionalBundlesConfig, additionalClassesConfig);
        dependencyGenerator.createCoreDependencies();
        logger.debug("java223 configuration update received ({})", properties);
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
            JavaScriptEngine engine = new Java223ScriptEngine();

            engine.setExecutionStrategyFactory(java223Strategy);
            engine.setBindingStrategy(java223Strategy);
            engine.setPackageResourceListingStrategy(osgiPackageResourceListingStrategy);
            engine.setCompilationStrategy(java223Strategy);
            engine.setScriptInterceptorStrategy(scriptWrappingStrategy);
            engine.setCompilationOptions(Arrays.asList("-g", "-parameters"));

            return engine;
        }
        return null;
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return createScriptEngine(Java223Constants.JAVA_FILE_TYPE);
    }

    /**
     * Additionnal data to put into bindings so the scripts could use them.
     *
     * @return
     */
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
                classGenerator.generateActions();
            }
        } else if (ITEM_EVENTS.contains(eventType)) {
            logger.debug("Added/updated item: {}", event);
            classGenerator.generateItems();
        } else if (THING_EVENTS.contains(eventType)) {
            logger.debug("Added/updated thing: {}", event);
            classGenerator.generateThings();
        }
    }
}
