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
import java.util.ArrayList;
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
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a JAR with some dependencies usefull to code from an external project
 *
 * @author Gwendal Roulleau - Initial contribution, based on work from Jan N. Klug
 */
@NonNullByDefault
public class DependencyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGenerator.class);

    private static final Set<String> DEFAULT_DEPENDENCIES = Set.of("org.openhab.automation.java223.common",
            "org.openhab.core.audio", "org.openhab.core.automation", "org.openhab.core.automation.events",
            "org.openhab.core.automation.util", "org.openhab.core.automation.module.script",
            "org.openhab.core.automation.module.script.defaultscope",
            "org.openhab.core.automation.module.script.rulesupport.shared",
            "org.openhab.core.automation.module.script.rulesupport.shared.simple", "org.openhab.core.common",
            "org.openhab.core.common.registry", "org.openhab.core.config.core", "org.openhab.core.events",
            "org.openhab.core.items", "org.openhab.core.items.events", "org.openhab.core.library.types",
            "org.openhab.core.library.items", "org.openhab.core.library.dimension", "org.openhab.core.model.script",
            "org.openhab.core.model.script.actions", "org.openhab.core.model.rule", "org.openhab.core.persistence",
            "org.openhab.core.persistence.extensions", "org.openhab.core.thing", "org.openhab.core.thing.events",
            "org.openhab.core.thing.binding", "org.openhab.core.transform", "org.openhab.core.transform.actions",
            "org.openhab.core.types", "org.openhab.core.voice", "com.google.gson");

    private static final Set<String> DEFAULT_CLASSES_DEPENDENCIES = Set.of("org.eclipse.jdt.annotation.NonNull",
            "org.eclipse.jdt.annotation.NonNullByDefault", "org.eclipse.jdt.annotation.Nullable",
            "org.eclipse.jdt.annotation.DefaultLocation", "org.slf4j.LoggerFactory", "org.slf4j.Logger",
            "org.slf4j.Marker");

    private Path libDir;
    private String additionalBundlesConfig;
    private String additionalClassesConfig;
    private BundleContext bundleContext;

    private Set<String> additionalClassesToExport = new HashSet<>();

    public DependencyGenerator(Path libDir, String additionalBundlesConfig, String additionalClassesConfig,
            BundleContext bundleContext) {
        super();
        this.libDir = libDir;
        this.additionalBundlesConfig = additionalBundlesConfig;
        this.additionalClassesConfig = additionalClassesConfig;
        this.bundleContext = bundleContext;
    }

    public void setAdditionalConfig(String additionalBundlesConfig, String additionalClassesConfig) {
        this.additionalBundlesConfig = additionalBundlesConfig;
        this.additionalClassesConfig = additionalClassesConfig;
    }

    public synchronized void createCoreDependencies() {
        try (FileOutputStream outFile = new FileOutputStream(
                libDir.resolve("jar").resolve("convenience-dependencies.jar").toFile())) {
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

            Set<String> packagesSuccessfullyExported = new HashSet<>();
            for (Bundle bundle : bundleContext.getBundles()) {
                if (searchIn.contains(bundle.getSymbolicName())) {
                    copyExportedPackagesByBundleInspection(dependencies, bundle, target, packagesSuccessfullyExported);
                }
            }

            Set<String> packagesNotFound = new HashSet<>(DEFAULT_DEPENDENCIES);
            packagesNotFound.removeAll(packagesSuccessfullyExported.stream().map(s -> s.replaceAll("/", ".")).toList());
            for (String remainingPackage : packagesNotFound) {
                logger.warn("Failed to found classes to export in package {}", remainingPackage);
            }

            Set<String> classesDependencies = new HashSet<>(DEFAULT_CLASSES_DEPENDENCIES);
            classesDependencies.addAll(Arrays.asList(additionalClassesConfig.split(",")));
            classesDependencies.addAll(additionalClassesToExport);
            copyExportedClassesByClassLoader(classesDependencies, target);

            target.close();
        } catch (IOException e) {
            logger.warn("Failed to create dependencies jar in '{}': {}", libDir, e.getMessage());
        }
    }

    private static void copyExportedClassesByClassLoader(Set<String> classesToExtract, JarOutputStream target) {
        for (String classToExtract : classesToExtract) {
            if (classToExtract.isEmpty()) {
                continue;
            }
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

    private static void copyExportedPackagesByBundleInspection(Set<String> dependencies, Bundle bundle,
            JarOutputStream target, Set<String> classesSuccessfullyExported) {
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
                                    classesSuccessfullyExported.add(packageName);
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

    private boolean excludeFromExport(String classToExport) {
        return classToExport.startsWith("java.") || DEFAULT_DEPENDENCIES.stream() //
                .filter(packageAlreadyExported -> classToExport.startsWith(packageAlreadyExported)) //
                .findFirst() //
                .isPresent();
    }

    private static void getAllInterfaces(@Nullable Class<?> cls, final Set<String> interfacesFound) {
        @Nullable
        Class<?> clsLocal = cls;
        while (clsLocal != null) {
            final Class<?>[] interfaces = clsLocal.getInterfaces();
            for (final Class<?> i : interfaces) {
                if (interfacesFound.add(i.getCanonicalName())) {
                    getAllInterfaces(i, interfacesFound);
                }
            }
            clsLocal = clsLocal.getSuperclass();
        }
    }

    public static List<String> getAllSuperclasses(final Class<?> cls) {
        final List<String> classes = new ArrayList<>();
        Class<?> superclass = cls.getSuperclass();
        while (superclass != null) {
            classes.add(superclass.getCanonicalName());
            superclass = superclass.getSuperclass();
        }
        return classes;
    }

    public void setClassesToAddToDependenciesLib(Set<String> allClassesToExport) {

        Set<String> newAdditionalClassesToExport = new HashSet<>();
        for (String clazzAsString : allClassesToExport) {
            Class<?> clazz;
            try {
                clazz = Class.forName(clazzAsString);
                newAdditionalClassesToExport.add(clazzAsString);
                newAdditionalClassesToExport.addAll(getAllSuperclasses(clazz));
                getAllInterfaces(clazz, newAdditionalClassesToExport);
            } catch (ClassNotFoundException e) {
                logger.warn("Cannot inspect class {} to add it as a dependancy", clazzAsString);
            }
        }

        newAdditionalClassesToExport.removeIf(this::excludeFromExport);
        // check if there is new classes :
        newAdditionalClassesToExport.removeAll(additionalClassesToExport);
        if (!newAdditionalClassesToExport.isEmpty()) {
            additionalClassesToExport.addAll(newAdditionalClassesToExport);
            createCoreDependencies();
        }
    }
}
