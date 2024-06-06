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

import static org.osgi.framework.wiring.BundleWiring.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a JAR with some dependencies needed to design rule from an external project
 *
 * @author Jan N. Klug - Initial contribution
 * @author Gwendal Roulleau
 */
@NonNullByDefault
public class DependencyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGenerator.class);

    private static final Set<String> DEFAULT_DEPENDENCIES = Set.of("javax.measure", "javax.measure.quantity",
            "org.openhab.core.audio", "org.openhab.core.automation", "org.openhab.core.automation.util",
            "org.openhab.core.automation.module.script", "org.openhab.core.automation.module.script.defaultscope",
            "org.openhab.core.automation.module.script.rulesupport.shared",
            "org.openhab.core.automation.module.script.rulesupport.shared.simple", "org.openhab.core.common",
            "org.openhab.core.common.registry", "org.openhab.core.config.core", "org.openhab.core.items",
            "org.openhab.core.library.types", "org.openhab.core.library.items", "org.openhab.core.library.dimension",
            "org.openhab.core.model.script", "org.openhab.core.model.script.actions", "org.openhab.core.model.rule",
            "org.openhab.core.persistence", "org.openhab.core.persistence.extensions", "org.openhab.core.thing",
            "org.openhab.core.thing.binding", "org.openhab.core.transform", "org.openhab.core.transform.actions",
            "org.openhab.core.types", "org.openhab.core.voice", "com.google.gson",
            "org.openhab.automation.java223.annotations", "org.openhab.automation.java223.helper",
            "org.openhab.automation.java223.helper.eventinfo", "org.openhab.automation.java223.helper.annotations",
            "org.openhab.automation.java223.eventinfo", "org.eclipse.jdt.annotation");

    public static synchronized void createCoreDependencies(Path libDir, String additionalBundlesConfig,
            BundleContext bundleContext) {
        try (FileOutputStream outFile = new FileOutputStream(libDir.resolve("dependencies.jar").toFile())) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            JarOutputStream target = new JarOutputStream(outFile, manifest);

            Set<String> dependencies = new HashSet<>(DEFAULT_DEPENDENCIES);
            dependencies.addAll(Arrays.asList(additionalBundlesConfig.split(",")));

            Set<String> searchIn = new HashSet<>();
            for (String packageName : dependencies) {
                String[] packageComponents = packageName.split("\\.");
                for (int i = 1; i <= packageComponents.length; i++) {
                    searchIn.add(String.join(".", Arrays.copyOfRange(packageComponents, 0, i)));
                }
            }

            for (Bundle bundle : bundleContext.getBundles()) {
                if (searchIn.contains(bundle.getSymbolicName())) {
                    copyExportedClasses(dependencies, bundle, target);
                }
            }

            exportJdtNullAnnotation(target);

            target.close();
        } catch (IOException e) {
            logger.warn("Failed to create dependencies jar in '{}': {}", libDir, e.getMessage());
        }
    }

    private static void exportJdtNullAnnotation(JarOutputStream target) {

        List<String> classesToExtract = List.of("org.eclipse.jdt.annotation.NonNull",
                "org.eclipse.jdt.annotation.NonNullByDefault", "org.eclipse.jdt.annotation.Nullable");
        for (String classToExtract : classesToExtract) {
            String path = classToExtract.replaceAll("\\.", "/") + ".class";
            ClassLoader classLoader = DependencyGenerator.class.getClassLoader();
            if (classLoader == null) {
                logger.warn("Failed (no classloader) to copy null annotation classes '{}' from classpath : {}",
                        classToExtract);
                return;
            }
            try (InputStream stream = classLoader.getResourceAsStream(path)) {
                if (stream != null) {
                    addEntryToJar(target, path, 0, stream);
                } else {
                    logger.warn("InputStream {} from classpath is null", classToExtract);
                }
            } catch (IOException e) {
                logger.warn("Failed to copy null annotation classes '{}' from classpath : {}", classToExtract,
                        e.getMessage());
            }
        }
    }

    private static void copyExportedClasses(Set<String> dependencies, Bundle bundle, JarOutputStream target) {
        String exportPackage = bundle.getHeaders().get("Export-Package");
        if (exportPackage == null) {
            logger.warn("Bundle '{}' does not export any package!", bundle.getSymbolicName());
            return;
        }
        List<String> exportedPackages = Arrays.stream(exportPackage //
                .split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) // split only on comma not in double quotes
                .map(s -> s.split(";")[0]) // get only package name and drop uses, version, etc.
                .map(b -> b.replace(".", "/")).collect(Collectors.toList());
        Set<String> dependenciesWithSlash = dependencies.stream().map(b -> b.replace(".", "/"))
                .collect(Collectors.<String>toSet());

        bundle.adapt(BundleWiring.class).listResources("", "*.class", LISTRESOURCES_LOCAL + LISTRESOURCES_RECURSE)
                .forEach(classFile -> {
                    try {
                        int classNameStart = classFile.lastIndexOf("/");
                        if (classNameStart != -1) {
                            String packageName = classFile.substring(0, classNameStart);
                            if (classNameStart == -1 || !exportedPackages.contains(packageName)
                                    || !dependenciesWithSlash.contains(packageName)) {
                                return;
                            }

                            URL urlEntry = bundle.getEntry(classFile);
                            if (urlEntry == null) {
                                logger.warn("URL for {} is empty, skipping", classFile);
                            } else {
                                try (InputStream stream = urlEntry.openStream()) {
                                    addEntryToJar(target, classFile, 0, stream);
                                }
                            }
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to copy class '{}' from '{}': {}", classFile, bundle.getSymbolicName(),
                                e.getMessage());
                    }
                });
    }

    private static void addEntryToJar(JarOutputStream jar, String name, long lastModified, InputStream content)
            throws IOException {
        JarEntry jarEntry = new JarEntry(name);
        if (lastModified != 0) {
            jarEntry.setTime(lastModified);
        }
        jar.putNextEntry(jarEntry);
        jar.write(content.readAllBytes());
        jar.closeEntry();
    }
}
