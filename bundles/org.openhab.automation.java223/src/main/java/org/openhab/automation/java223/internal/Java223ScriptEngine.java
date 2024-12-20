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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Collectors;

import javax.script.Invocable;
import javax.script.ScriptException;
import javax.tools.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.ScriptLoadedTrigger;
import org.openhab.automation.java223.common.ScriptUnloadedTrigger;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.Isolation;
import ch.obermuhlner.scriptengine.java.JavaScriptEngine;
import ch.obermuhlner.scriptengine.java.MemoryFileManager;
import ch.obermuhlner.scriptengine.java.name.NameStrategy;

/**
 * This class adds a cache for compiled script to Obermuhlner's base class.
 * This class also adds the Invocable aspect to the JavaScriptEngine. The Invocable aspect adds the ability to be called
 * when loaded and unloaded script event are triggered.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class Java223ScriptEngine extends JavaScriptEngine implements Invocable {
    private final Logger logger = LoggerFactory.getLogger(Java223ScriptEngine.class);

    private @Nullable Java223CompiledScript lastCompiledScript;

    private final Java223CompiledScriptCache cache;

    private final Java223Strategy java223Strategy;

    public Java223ScriptEngine(Java223CompiledScriptCache cache, Java223Strategy java223Strategy) {
        super();
        this.cache = cache;
        this.java223Strategy = java223Strategy;
        setExecutionStrategyFactory(java223Strategy);
        setBindingStrategy(java223Strategy);
        setCompilationStrategy(java223Strategy);
        setConstructorStrategy(java223Strategy);
    }

    /**
     * Recopy super compile implementation with small changes
     * 
     * @param script The script to compile
     * @return A compiled instance
     * @throws ScriptException when script has error
     */
    private Java223CompiledScript internalCompilation(String script) throws ScriptException {

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        ClassLoader parentClassLoader = isolation == Isolation.CallerClassLoader ? executionClassLoader : null;
        JavaFileManager fileManager = compilationStrategy.getJavaFileManager(
                ToolProvider.getSystemJavaCompiler().getStandardFileManager(diagnostics, null, null));
        if (fileManager == null) {
            fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        } else {
            parentClassLoader = fileManager.getClassLoader(StandardLocation.CLASS_PATH);
        }
        MemoryFileManager memoryFileManager = new MemoryFileManager(fileManager, parentClassLoader);

        memoryFileManager.setPackageResourceListingStrategy(packageResourceListingStrategy);

        String fullClassName = nameStrategy.getFullName(script);
        String simpleClassName = NameStrategy.extractSimpleName(fullClassName);

        List<JavaFileObject> toCompile = compilationStrategy.getJavaFileObjectsToCompile(simpleClassName, script);

        JavaCompiler.CompilationTask task = compiler.getTask(null, memoryFileManager, diagnostics, compilationOptions,
                null, toCompile);
        if (!task.call()) {
            String message = diagnostics.getDiagnostics().stream().map(Object::toString)
                    .collect(Collectors.joining("\n"));
            throw new ScriptException(message);
        }

        ClassLoader classLoader = memoryFileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);

        try {
            Class<?> clazz = classLoader.loadClass(fullClassName);
            compilationStrategy.compilationResult(clazz);
            // Object instance = constructorStrategy.construct(clazz);
            // ExecutionStrategy executionStrategy = executionStrategyFactory.create(clazz);
            return new Java223CompiledScript(this, clazz, java223Strategy);
        } catch (ClassNotFoundException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Java223CompiledScript compile(@Nullable String originalScript) throws ScriptException {
        try {
            logger.error("oulala");
            if (originalScript == null) {
                throw new ScriptException("script cannot be null");
            }
            String script = scriptInterceptorStrategy.intercept(originalScript);

            // reuse the original compilation class, potentially cached
            var compiledScriptResult = cache.getOrCompile(script, this::internalCompilation);
            lastCompiledScript = compiledScriptResult;
            return compiledScriptResult;
        } catch (NoClassDefFoundError e) {
            throw new ScriptException("NoClassDefFoundError: " + e.getMessage());
        }
    }

    @Override
    public @Nullable Object invokeMethod(@Nullable Object o, @Nullable String name, Object @Nullable... args)
            throws NoSuchMethodException {
        throw new NoSuchMethodException("not implemented");
    }

    @Override
    public @Nullable Object invokeFunction(@Nullable String name, Object @Nullable... args) throws ScriptException {

        // here we assume (from OpenHAB usual behavior) that the script engine served only once and so the wanted
        // compiled script is the last (and only) one
        Java223CompiledScript compiledScript = this.lastCompiledScript;
        if (compiledScript == null || name == null) {
            return null;
        }

        Class<? extends Annotation> annotation = switch (name) {
            case "scriptLoaded" -> ScriptLoadedTrigger.class;
            case "scriptUnloaded" -> ScriptUnloadedTrigger.class;
            default -> throw new ScriptException(name + " is not an allowed method in java223");
        };

        Object compiledInstance = compiledScript.getCompiledInstance();
        for (Method method : compiledScript.getCompiledClass().getMethods()) {
            Annotation scriptLoadedOrUnloadedAnnotation = method.getAnnotation(annotation);
            if (scriptLoadedOrUnloadedAnnotation != null) {
                if (method.getParameters().length != 0) {
                    throw new ScriptException("Method " + method.getName()
                            + " called by ScriptLoaded/ScriptUnloaded trigger should not have any argument");
                } else {
                    try {
                        if (Modifier.isStatic(method.getModifiers())) {
                            method.invoke(new Object()); // new object() required (but value ignored) to avoid non-null
                                                         // check compiler error
                        } else if (compiledInstance == null) {
                            logger.debug(
                                    "Calling ScriptLoaded/ScriptUnloaded {} method from a script not yet instantiated is ignored. Use a static modifier",
                                    method.getName());
                        } else {
                            method.invoke(compiledInstance);
                        }
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        logger.warn("Method {} cannot be called by ScriptLoaded/ScriptUnloaded trigger",
                                method.getName());
                        throw new ScriptException(e);
                    }
                }
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("null")
    public <T> T getInterface(@Nullable Class<T> clazz) {
        return null;
    }

    @Override
    @SuppressWarnings("null")
    public <T> T getInterface(@Nullable Object o, @Nullable Class<T> clazz) {
        return null;
    }
}
