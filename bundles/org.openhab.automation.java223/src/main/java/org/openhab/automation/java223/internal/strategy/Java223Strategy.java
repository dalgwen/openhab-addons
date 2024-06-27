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

package org.openhab.automation.java223.internal.strategy;

import static org.openhab.automation.java223.common.Java223Constants.LIB_DIR;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.script.ScriptException;
import javax.tools.JavaFileObject;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.BindingInjector;
import org.openhab.automation.java223.common.Java223Constants;
import org.openhab.automation.java223.common.RunScript;
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
 * @author Gwendal Roulleau
 */
public class Java223Strategy implements ExecutionStrategyFactory, ExecutionStrategy, BindingStrategy,
        CompilationStrategy, WatchService.WatchEventListener {

    private static Logger logger = LoggerFactory.getLogger(Java223Strategy.class);

    private static final List<String> METHOD_NAMES_TO_EXECUTE = Arrays.asList("eval", "main", "run");

    // Additional bindings, not in the openhab JSR 223 specification
    private static Map<String, Object> additionalBindings;

    // Keeping a list of library class
    private static Map<String, JavaFileObject> librariesByFullClassName = new HashMap<>();
    private static Map<String, String> librariesFullClassNameByPath = new HashMap<>();

    NameStrategy nameStrategy = new DefaultNameStrategy();

    // Store bindings temporary to inject it as a method parameter during execution phase
    private BindingsStore bindingsStore = new BindingsStore();

    public Java223Strategy(Map<String, Object> additionalBindings) {
        super();
        Java223Strategy.additionalBindings = additionalBindings;
    }

    @Override
    public ExecutionStrategy create(@Nullable Class<?> clazz) throws ScriptException {
        return this;
    }

    @Override
    public void associateBindings(Class<?> compiledClass, Object compiledInstance, Map<String, Object> bindings) {

        // adding a special self reference to bindings : "bindings", to receive a map with all bindings
        bindings.put("bindings", bindings);

        // adding some custom additional fields
        bindings.putAll(additionalBindings);

        // storing bindings to be used as parameter in case of deferred execution
        bindingsStore.addBindings(compiledInstance, bindings);

        // finally, inject bindings data in the script
        BindingInjector.injectBindingsInto(bindings, compiledInstance);
    }

    @Override
    public @Nullable Object execute(@Nullable Object instance) throws ScriptException {
        if (instance == null) {
            throw new ScriptException("Cannot run null class/instance");
        }

        // try to execute
        try {
            Optional<Object> returned = null;
            for (Method method : instance.getClass().getMethods()) {
                if (METHOD_NAMES_TO_EXECUTE.contains(method.getName())
                        || method.getAnnotation(RunScript.class) != null) {
                    try {
                        Map<String, Object> bindings = bindingsStore.getBindings(instance);
                        if (bindings == null) {
                            throw new ScriptException(
                                    String.format("Error executing entry point %s in %s : bindings is null",
                                            method.getName(), instance.getClass().getSimpleName()));
                        }
                        Object[] parameterValues = BindingInjector.getParameterValuesFor(method, bindings, null);
                        returned = Optional.ofNullable(method.invoke(instance, parameterValues));
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        String simpleName = instance.getClass().getSimpleName();
                        logger.error("Error executing entry point {} in {}", method.getName(), simpleName, e);
                        throw new ScriptException(
                                String.format("Error executing entry point %s in %s", method.getName(), simpleName, e));
                    }
                }
            }
            // arbitrary choose to return the last execution call result :
            if (returned != null) {
                return returned;
            }

            throw new ScriptException(String.format("cannot execute: %s doesn't have an eval/main/run method",
                    instance.getClass().getSimpleName()));
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Map<String, Object> retrieveBindings(Class<?> compiledClass, Object compiledInstance) {
        return new HashMap<String, Object>();
    }

    @Override
    public List<JavaFileObject> getJavaFileObjectsToCompile(@Nullable String simpleClassName,
            @Nullable String currentSource) {
        JavaFileObject currentJavaFileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName,
                currentSource);
        List<JavaFileObject> sumFileObjects = new ArrayList<>(librariesByFullClassName.values());
        sumFileObjects.add(currentJavaFileObject);
        return sumFileObjects;
    }

    @Override
    public void processWatchEvent(WatchService.Kind kind, Path path) {
        Path fullPath = LIB_DIR.resolve(path);
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
        } else {
            logger.trace("Received '{}' for path '{}' - ignoring (wrong extension)", kind, fullPath);
        }
    }

    private void addLibrary(Path path) {
        try {
            String readString = Files.readString(path);
            String fullName = nameStrategy.getFullName(readString);
            String simpleClassName = NameStrategy.extractSimpleName(fullName);
            JavaFileObject javafileObject = MemoryFileManager.createSourceFileObject(null, simpleClassName, readString);
            librariesFullClassNameByPath.put(path.toString(), fullName);
            librariesByFullClassName.put(fullName, javafileObject);
        } catch (ScriptException | IOException e) {
            logger.info("Cannot get the file {} as a valid java object. Cause: {} {}", path.toString(),
                    e.getClass().getName(), e.getMessage());
        }
    }

    private void removeLibrary(Path path) {
        String fullClassName = librariesFullClassNameByPath.remove(path.toString());
        if (fullClassName != null) {
            librariesByFullClassName.remove(fullClassName);
        }
    }

    public void scanLibDirectory() {
        try {
            Files.walk(LIB_DIR).filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("." + Java223Constants.JAVA_FILE_TYPE))
                    .forEach(this::addLibrary);
        } catch (IOException e) {
            logger.error("Cannot use libraries", e);
        }
    }

    public static boolean containsLibrary(String name) {
        return librariesByFullClassName.containsKey(name);
    }

    private class BindingsStore {
        private Map<Object, BindingStoreEntry> mapStorage = new HashMap<>();

        private void addBindings(Object instance, Map<String, Object> bindings) {
            clearOld();
            mapStorage.put(instance, new BindingStoreEntry(System.currentTimeMillis(), bindings));
        }

        @Nullable
        private Map<String, Object> getBindings(Object scriptInstance) {
            BindingStoreEntry bindingsForObject = mapStorage.remove(scriptInstance);
            if (bindingsForObject != null) {
                return bindingsForObject.bindings;
            } else {
                return null;
            }
        }

        /**
         * Clear bindings stored for more 50s.
         * associateBindings and execute should be called sequentially, 50s is plenty enough
         * (unless there is a deeper problem)
         */
        private void clearOld() {
            Long now = System.currentTimeMillis();
            for (Iterator<@NonNull Entry<Object, BindingStoreEntry>> it = mapStorage.entrySet().iterator(); it
                    .hasNext();) {
                Entry<Object, BindingStoreEntry> next = it.next();
                if (now - next.getValue().timeStamp() > 50000) {
                    it.remove();
                }
            }
        }
    }

    private record BindingStoreEntry(Long timeStamp, Map<String, Object> bindings) {
    }
}
