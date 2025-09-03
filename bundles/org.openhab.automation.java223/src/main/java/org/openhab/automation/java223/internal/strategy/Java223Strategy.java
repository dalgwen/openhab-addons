/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.automation.java223.internal.strategy;

import static org.openhab.automation.java223.common.Java223Constants.LIB_DIR;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.script.ScriptException;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.BindingInjector;
import org.openhab.automation.java223.common.Java223Constants;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.automation.java223.common.ReuseScriptInstance;
import org.openhab.automation.java223.common.RunScript;
import org.openhab.automation.java223.internal.Java223CompiledScript;
import org.openhab.automation.java223.internal.codegeneration.DependencyGenerator;
import org.openhab.automation.java223.internal.strategy.jarloader.JarFileManager.JarFileManagerFactory;
import org.openhab.core.service.WatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.MemoryFileManager;
import ch.obermuhlner.scriptengine.java.bindings.BindingStrategy;
import ch.obermuhlner.scriptengine.java.compilation.CompilationStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;
import ch.obermuhlner.scriptengine.java.name.DefaultNameStrategy;
import ch.obermuhlner.scriptengine.java.name.NameStrategy;

/**
 * A one-for-all strategy for a goal : providing binding / execution / library compilation and management to java223
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223Strategy
        implements ExecutionStrategyFactory, ExecutionStrategy, BindingStrategy, CompilationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(Java223Strategy.class);

    private static final List<String> METHOD_NAMES_TO_EXECUTE = Arrays.asList("eval", "main", "run", "exec");

    // Additional bindings, not in the openhab JSR 223 specification
    private final Map<String, Object> additionalBindings;

    // Keeping a list of library .java file in the lib directory
    private static final Map<String, JavaFileObject> librariesByPath = new HashMap<>();

    NameStrategy nameStrategy = new DefaultNameStrategy();
    JarFileManagerFactory jarFileManagerfactory;

    private boolean allowInstanceReuseDefaultProperty;

    public Java223Strategy(Map<String, Object> additionalBindings, ClassLoader classLoader) {
        this.additionalBindings = additionalBindings;
        this.allowInstanceReuseDefaultProperty = false;
        jarFileManagerfactory = new JarFileManagerFactory(LIB_DIR, classLoader);
    }

    @Override
    public ExecutionStrategy create(@Nullable Class<?> clazz) throws ScriptException {
        return this;
    }

    /**
     * Add data in bindings. Do not use the compiledClass or compiledInstance.
     * 
     * @param compiledClass Not used
     * @param compiledInstance Not used
     * @param bindings Map to add special data inside
     */
    @Override
    public void associateBindings(@Nullable Class<?> compiledClass, @Nullable Object compiledInstance,
            Map<String, Object> bindings) {
        // adding a special self reference to bindings : "bindings", to receive a map with all bindings
        bindings.put("bindings", bindings);
        // adding some custom additional fields
        bindings.putAll(additionalBindings);
    }

    @Override
    public @Nullable Object execute(@Nullable Object instance) throws ScriptException {
        throw new UnsupportedOperationException(
                "Wrong way to use this strategy. Use execute(script, bindings instead)");
    }

    /**
     * Contrary to the original architecture, this executes method doesn't use an instance, but the CompiledScript
     * itself. It is indeed responsible for instantiation, with the bindings data.
     * 
     * @param instance an instantiated script
     * @param bindings bindings data to inject
     * @return Execution result
     * @throws ScriptException When script cannot execute
     */
    public @Nullable Object execute(Object instance, Map<String, Object> bindings) throws ScriptException {

        Class<?> compiledClass = instance.getClass();

        // inject bindings data in the script
        ClassLoader classLoader = compiledClass.getClassLoader();
        if (classLoader == null) { // should not happen
            throw new Java223Exception("Cannot get the classloader of " + compiledClass.getName());
        }
        BindingInjector.injectBindingsInto(classLoader, bindings, instance);

        // find methods to execute
        Optional<Object> returned = null;
        for (Method method : instance.getClass().getMethods()) {
            // methods with a special name, or methods with a special annotation
            if (METHOD_NAMES_TO_EXECUTE.contains(method.getName()) || method.getAnnotation(RunScript.class) != null) {
                try {
                    Object[] parameterValues = BindingInjector.getParameterValuesFor(classLoader, method, bindings,
                            null);
                    var returnedLocal = method.invoke(instance, parameterValues);
                    // keep arbitrarily only the first returned value
                    if (returned == null || returned.isEmpty()) {
                        if (returnedLocal != null) {
                            returned = Optional.of(returnedLocal);
                        } else {
                            returned = Optional.empty();
                        }
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                        | InstantiationException e) {
                    String simpleName = instance.getClass().getSimpleName();
                    logger.error("Error executing entry point {} in {}", method.getName(), simpleName, e);
                    throw new ScriptException(String.format("Error executing entry point %s in %s, exception %s",
                            method.getName(), simpleName, e.getMessage()));
                }
            }
        }

        // return if there was at least one execution
        if (returned != null) {
            return returned.orElse(null);
        }

        throw new ScriptException(String.format(
                "cannot execute: %s doesn't have a method named eval/main/run, or a RunScript annotated method",
                compiledClass.getSimpleName()));
    }

    @Override
    public Map<String, Object> retrieveBindings(Class<?> compiledClass, Object compiledInstance) {
        // not needed ? What is the use case ?
        return new HashMap<>();
    }

    @Override
    public List<JavaFileObject> getJavaFileObjectsToCompile(@Nullable String simpleClassName,
            @Nullable String currentSource) {
        // the script
        JavaFileObject currentJavaFileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName,
                currentSource);
        // and we add all the .java libraries
        List<JavaFileObject> sumFileObjects = new ArrayList<>(librariesByPath.values());
        sumFileObjects.add(currentJavaFileObject);
        return sumFileObjects;
    }

    public void processWatchEvent(WatchService.Kind kind, Path pathEvent) {
        Path fullPath = LIB_DIR.resolve(pathEvent);

        // All new .java file will be kept in memory
        if (fullPath.getFileName().toString().endsWith("." + Java223Constants.JAVA_FILE_TYPE)) {
            switch (kind) {
                case CREATE:
                case MODIFY:
                    addLibrary(fullPath);
                    break;
                case DELETE:
                    removeLibrary(fullPath);
                    break;
                default:
                    logger.warn("watch event not implemented {}", kind);
            }
        } else if (fullPath.getFileName().toString().endsWith("." + Java223Constants.JAR_FILE_TYPE)) {
            // jar will be scanned to be added to the JarFileManagerFactory
            // exclude convenience jar from processing
            if (fullPath.getFileName().toString().equals(DependencyGenerator.CONVENIENCE_DEPENDENCIES_JAR)) {
                return;
            }
            switch (kind) {
                case CREATE:
                    jarFileManagerfactory.addLibPackage(fullPath);
                    break;
                case MODIFY:
                case DELETE:
                    // we cannot remove something from a ClassLoader, so we have to rebuild it
                    logger.debug("From watch event {} {}", kind, pathEvent);
                    jarFileManagerfactory.rebuildLibPackages();
                    break;
                case OVERFLOW:
                    break;
            }
        } else {
            logger.trace(
                    "Received '{}' for path '{}' - ignoring (wrong extension, only .java and .jar file are supported)",
                    kind, fullPath);
        }
    }

    private void addLibrary(Path path) {
        try {
            String readString = Files.readString(path);
            String fullName = nameStrategy.getFullName(readString);
            String simpleClassName = NameStrategy.extractSimpleName(fullName);
            JavaFileObject javafileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName, readString);
            librariesByPath.put(path.toString(), javafileObject);
        } catch (ScriptException | IOException e) {
            logger.warn("Cannot get the file {} as a valid java object. Cause: {} {}", path, e.getClass().getName(),
                    e.getMessage());
        }
    }

    private void removeLibrary(Path path) {
        librariesByPath.remove(path.toString());
    }

    public void scanLibDirectory() {
        try (Stream<Path> walk = Files.walk(LIB_DIR)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("." + Java223Constants.JAVA_FILE_TYPE))
                    .forEach(this::addLibrary);
            jarFileManagerfactory.rebuildLibPackages();
        } catch (IOException e) {
            logger.error("Cannot use libraries", e);
        }
    }

    @Override
    public JavaFileManager getJavaFileManager(@Nullable JavaFileManager parentJavaFileManager) {
        if (parentJavaFileManager == null) {
            throw new IllegalArgumentException("Parent JavaFileManager should not be null");
        }
        return jarFileManagerfactory.create(parentJavaFileManager);
    }

    @SuppressWarnings("null")
    public Object construct(Java223CompiledScript compiledScript, Map<String, Object> bindings) {

        // default re-instantiation option overwritten by annotation if present
        boolean instanceReuse = allowInstanceReuseDefaultProperty;
        Class<?> compiledClass = null;
        try {
            compiledClass = compiledScript.getCompiledClassSafe();
        } catch (ScriptException e) {
            throw new Java223Exception("Cannot");
        }
        ReuseScriptInstance reuseAnnotation = compiledClass.getAnnotation(ReuseScriptInstance.class);
        if (reuseAnnotation != null) {
            instanceReuse = reuseAnnotation.value();
        }

        // if allowed, get from cache and return
        var alreadyExistingScriptInstance = compiledScript.getCompiledInstance();
        if (instanceReuse && alreadyExistingScriptInstance != null) {
            return alreadyExistingScriptInstance;
        }

        // create real instance from compiled class
        // use the empty constructor if available, or the first one otherwise
        Constructor<?>[] constructors = compiledClass.getDeclaredConstructors();
        Constructor<?> constructor = Arrays.stream(constructors).filter(c -> c.getParameterCount() == 0).findFirst()
                .orElseGet(() -> constructors[0]);

        try {
            ClassLoader classLoader = compiledClass.getClassLoader();
            if (classLoader == null) { // should not happen
                throw new Java223Exception("Cannot get the classloader of " + compiledClass.getName());
            }
            Object[] parameterValues = BindingInjector.getParameterValuesFor(classLoader, constructor, bindings, null);
            Object compiledInstance = constructor.newInstance(parameterValues);
            if (compiledInstance == null) { // can't be null but null-check think so
                throw new Java223Exception("Instantiation of compiledInstance failed. Should not happened");
            }
            compiledScript.setCompiledInStance(compiledInstance);
            return compiledInstance;
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new Java223Exception("Cannot instantiate the script", e);
        }
    }

    public void setAllowInstanceReuse(boolean allowInstanceReuse) {
        this.allowInstanceReuseDefaultProperty = allowInstanceReuse;
    }
}
