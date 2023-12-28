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
import java.util.stream.Stream;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.OpenHAB;
import org.openhab.core.automation.module.script.AbstractScriptEngineFactory;
import org.openhab.core.automation.module.script.ScriptEngineFactory;
import org.openhab.core.service.WatchService;
import org.openhab.core.service.WatchService.Kind;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.JavaScriptEngine;
import ch.obermuhlner.scriptengine.java.packagelisting.PackageResourceListingStrategy;

/**
 * This is an implementation of a {@link ScriptEngineFactory} for Java, based on
 * https://github.com/eobermuhlner/java-scriptengine/
 * by Eric Obermühlner
 *
 * @author Jürgen Weber - Initial contribution
 */
@Component(service = { ScriptEngineFactory.class, JavaScriptEngineFactory.class })
public class JavaScriptEngineFactory extends AbstractScriptEngineFactory implements WatchService.WatchEventListener {

    public static final Path LIB_DIR = Path.of(OpenHAB.getConfigFolder(), "automation", "lib",
            JavaScriptingConstants.JAVA_FILE_TYPE);

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptEngineFactory.class);

    BundleWiring bundleWiring;

    private ch.obermuhlner.scriptengine.java.JavaScriptEngineFactory javaScriptEngineFactory;

    private IncludeLibraryCompilationStrategy withLibrariesCompilationStrategy = new IncludeLibraryCompilationStrategy();

    private PackageResourceListingStrategy osgiPackageResourceListingStrategy;

    private final WatchService watchService;

    @Activate
    public JavaScriptEngineFactory(BundleContext bundleContext,
            @Reference(target = WatchService.CONFIG_WATCHER_FILTER) WatchService watchService) {
        this.watchService = watchService;

        osgiPackageResourceListingStrategy = new PackageResourceListingStrategy() {
            @Override
            public Collection<String> listResources(String packageName) {
                return listClassResources(packageName);
            }
        };

        javaScriptEngineFactory = new ch.obermuhlner.scriptengine.java.JavaScriptEngineFactory();

        bundleWiring = bundleContext.getBundle().adapt(BundleWiring.class);

        try {
            Files.createDirectories(LIB_DIR);
        } catch (IOException e) {
            logger.warn("Failed to create directory '{}': {}", LIB_DIR, e.getMessage());
            throw new IllegalStateException("Failed to initialize lib folder.");
        }

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

            JavaScriptEngine engine = (JavaScriptEngine) javaScriptEngineFactory.getScriptEngine();

            engine.setExecutionStrategyFactory(new EntryExecutionStrategyFactory());

            engine.setPackageResourceListingStrategy(osgiPackageResourceListingStrategy);

            engine.setBindingStrategy(new BulkBindingStrategy());

            engine.setCompilationStrategy(withLibrariesCompilationStrategy);

            engine.setScriptInterceptorStrategy(new ScriptWrappingStategy());

            engine.setCompilationOptions(Arrays.asList("-g"));

            return engine;
        }
        return null;
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
        } catch (IOException | ScriptException e) {
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
}
