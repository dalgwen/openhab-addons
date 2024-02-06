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
package org.openhab.automation.javascripting.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.script.ScriptEngine;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.javascripting.common.JavaScriptingConstants;
import org.openhab.automation.javascripting.internal.codegeneration.ClassGenerator;
import org.openhab.automation.javascripting.internal.codegeneration.DependencyGenerator;
import org.openhab.core.OpenHAB;
import org.openhab.core.automation.RuleManager;
import org.openhab.core.automation.module.script.AbstractScriptEngineFactory;
import org.openhab.core.automation.module.script.ScriptEngineFactory;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.items.events.ItemAddedEvent;
import org.openhab.core.items.events.ItemRemovedEvent;
import org.openhab.core.service.WatchService;
import org.openhab.core.service.WatchService.Kind;
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
import ch.obermuhlner.scriptengine.java.compilation.ScriptInterceptorStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;
import ch.obermuhlner.scriptengine.java.packagelisting.PackageResourceListingStrategy;
import freemarker.template.TemplateException;

/**
 * This is an implementation of a {@link ScriptEngineFactory} for Java, based on
 * https://github.com/eobermuhlner/java-scriptengine/
 * by Eric Obermühlner
 *
 * @author Jürgen Weber - Initial contribution
 */
@Component(service = { ScriptEngineFactory.class, JavaScriptEngineFactory.class, EventSubscriber.class })
public class JavaScriptEngineFactory extends AbstractScriptEngineFactory
        implements EventSubscriber, WatchService.WatchEventListener {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptEngineFactory.class);

    public static final Path LIB_DIR = Path.of(OpenHAB.getConfigFolder(), "automation", "lib",
            JavaScriptingConstants.JAVA_FILE_TYPE);

    private BundleWiring bundleWiring;
    private BundleContext bundleContext;

    private ch.obermuhlner.scriptengine.java.JavaScriptEngineFactory javaScriptEngineFactory;
    private IncludeLibraryCompilationStrategy withLibrariesCompilationStrategy;
    private PackageResourceListingStrategy osgiPackageResourceListingStrategy;
    private BulkBindingStrategy bulkBindingStrategy;
    private ExecutionStrategyFactory entryExecutionStrategy;
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
    public JavaScriptEngineFactory(BundleContext bundleContext, Map<String, Object> properties,
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
        javaScriptEngineFactory = new ch.obermuhlner.scriptengine.java.JavaScriptEngineFactory();
        withLibrariesCompilationStrategy = new IncludeLibraryCompilationStrategy();
        bulkBindingStrategy = new BulkBindingStrategy(getAdditionalBindings());
        entryExecutionStrategy = new EntryExecutionStrategyFactory();
        scriptWrappingStrategy = new ScriptWrappingStategy();

        try {
            ClassGenerator localClassGenerator = new ClassGenerator(LIB_DIR, itemRegistry, thingRegistry,
                    bundleContext);
            this.classGenerator = localClassGenerator;
            String itemClass = localClassGenerator.generateItems();
            bulkBindingStrategy.setLibraryClassList("Items", List.of(itemClass));
            String thingClass = localClassGenerator.generateThings();
            bulkBindingStrategy.setLibraryClassList("Things", List.of(thingClass));
            List<String> thingActionsClasses = localClassGenerator.generateThingActions();
            bulkBindingStrategy.setLibraryClassList("Actions", thingActionsClasses);
            new DependencyGenerator().createCoreDependencies(LIB_DIR, additionalBundlesConfig, bundleContext);
        } catch (IOException | TemplateException e) {
            logger.error("Cannot create helper class file in library dir. " + e.getMessage());
        }

        this.watchService = watchService;
        scanLibDirectory();
        watchService.registerListener(this, LIB_DIR);

        logger.info("Bundle activated");
    }

    @Deactivate
    public void deactivate() {
        watchService.unregisterListener(this);
    }

    @Override
    public List<String> getScriptTypes() {
        String[] types = { JavaScriptingConstants.JAVA_FILE_TYPE };
        return Arrays.asList(types);
    }

    @Override
    public @Nullable ScriptEngine createScriptEngine(String scriptType) {
        if (getScriptTypes().contains(scriptType)) {

            if (this.scriptEngineInstance == null || !REUSE_SCRIPT_ENGINE) {
                JavaScriptEngine engine = (JavaScriptEngine) javaScriptEngineFactory.getScriptEngine();

                engine.setExecutionStrategyFactory(entryExecutionStrategy);
                engine.setPackageResourceListingStrategy(osgiPackageResourceListingStrategy);
                engine.setBindingStrategy(bulkBindingStrategy);
                engine.setCompilationStrategy(withLibrariesCompilationStrategy);
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

    private Map<String, Object> getAdditionalBindings() {
        RuleManager ruleManager = bundleContext.getService(bundleContext.getServiceReference(RuleManager.class));
        MetadataRegistry metadataRegistry = bundleContext
                .getService(bundleContext.getServiceReference(MetadataRegistry.class));
        return Map.of(JavaScriptingConstants.RULE_MANAGER, ruleManager, JavaScriptingConstants.METADATA_REGISTRY,
                metadataRegistry);
    }

    // Compiler wants classes in used packages

    private Collection<String> listClassResources(String packageName) {

        String path = packageName.replace(".", "/");
        path = "/" + path;

        Collection<String> resources = bundleWiring.listResources(path, "*.class", 0);

        return resources;
    }

    private void scanLibDirectory() {
        try {
            Stream<Path> javaFileWalk = Files.walk(LIB_DIR).filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("." + JavaScriptingConstants.JAVA_FILE_TYPE));
            withLibrariesCompilationStrategy.setLibraries(javaFileWalk.toList());
        } catch (IOException e) {
            logger.error("Cannot use libraries", e);
        }
    }

    @Override
    public void processWatchEvent(WatchService.Kind kind, Path path) {
        Path fullPath = LIB_DIR.resolve(path);
        if (fullPath.getFileName().toString().endsWith("." + JavaScriptingConstants.JAVA_FILE_TYPE)
                && (kind == Kind.CREATE || kind == Kind.MODIFY || kind == Kind.DELETE)) {
            scanLibDirectory();
        } else {
            logger.trace("Received '{}' for path '{}' - ignoring (wrong extension)", kind, fullPath);
        }
    }

    @Override
    public @NonNull Set<@NonNull String> getSubscribedEventTypes() {
        return EVENTS;
    }

    @Override
    public void receive(Event event) {
        String eventType = event.getType();

        ClassGenerator localClassGenerator = classGenerator;
        if (localClassGenerator == null) {
            logger.error("Cannot use the class generator, as its initialization was in error");
            return;
        }

        if (ACTION_EVENTS.contains(eventType)) {
            ThingStatusInfoChangedEvent event1 = (ThingStatusInfoChangedEvent) event;
            if ((ThingStatus.INITIALIZING.equals(event1.getOldStatusInfo().getStatus())
                    && INITIALIZED.contains(event1.getStatusInfo().getStatus()))
                    || (ThingStatus.UNINITIALIZED.equals(event1.getStatusInfo().getStatus())
                            && INITIALIZED.contains(event1.getOldStatusInfo().getStatus()))) {
                try {
                    List<@NonNull String> generateThingActions = localClassGenerator.generateThingActions();
                    bulkBindingStrategy.setLibraryClassList("Actions", generateThingActions);
                } catch (IOException | TemplateException e) {
                    logger.warn("Failed to (re-)build thing action classes: {}", e.getMessage());
                }
            }
        } else if (ITEM_EVENTS.contains(eventType)) {
            logger.debug("Added/updated item: {}", event);
            try {
                String itemClassName = localClassGenerator.generateItems();
                bulkBindingStrategy.setLibraryClassList("Items", List.of(itemClassName));
            } catch (IOException | TemplateException e) {
                logger.warn("Failed to (re-)build item class: {}", e.getMessage());
            }
        } else if (THING_EVENTS.contains(eventType)) {
            logger.debug("Added/updated thing: {}", event);
            try {
                String thingsClassName = localClassGenerator.generateThings();
                bulkBindingStrategy.setLibraryClassList("Things", List.of(thingsClassName));
            } catch (IOException | TemplateException e) {
                logger.warn("Failed to (re-)build thing class: {}", e.getMessage());
            }
        }
    }
}
