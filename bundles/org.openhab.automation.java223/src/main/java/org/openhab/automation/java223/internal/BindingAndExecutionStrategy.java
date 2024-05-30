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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.annotations.InjectBinding;
import org.openhab.automation.java223.common.Java223Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.bindings.BindingStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategy;
import ch.obermuhlner.scriptengine.java.execution.ExecutionStrategyFactory;

/**
 * @author Gwendal Roulleau
 */
public class BindingAndExecutionStrategy implements ExecutionStrategyFactory, ExecutionStrategy, BindingStrategy {

    private static Logger logger = LoggerFactory.getLogger(BindingAndExecutionStrategy.class);

    // Additional bindings, not in the openhab JSR 223 specification
    private Map<String, Object> additionalBindings;

    // Keeping here a list of library class to inject data into it
    private Map<String, List<String>> libraryClassList = new HashMap<>();

    // Store bindings temporary to inject it as a method parameter during execution phase
    private BindingsStore bindingsStore = new BindingsStore();

    public BindingAndExecutionStrategy(Map<String, Object> additionalBindings) {
        super();
        this.additionalBindings = additionalBindings;
    }

    public void setLibraryClassList(String category, List<@NonNull String> classList) {
        libraryClassList.put(category, classList);
    }

    @Override
    public ExecutionStrategy create(@Nullable Class<?> clazz) throws ScriptException {
        return this;
    }

    private @Nullable Object getBindingValue(Map<String, Object> bindings, String parameterKey) {
        if (parameterKey.contains(".")) {
            String presetToImport = parameterKey.substring(0, parameterKey.indexOf("."));
            String key = parameterKey.substring(parameterKey.indexOf("."));
            Map<String, Object> preset = null;
            try {
                preset = (Map<String, Object>) bindings.get(presetToImport);
            } catch (ClassCastException cce) {
            }
            if (preset == null) {
                logger.error("Cannot found preset {} asked to inject in parameter", preset);
            } else {
                return preset.get(key);
            }
        } else {
            return bindings.get(parameterKey);
        }
        return null;
    }

    @Override
    public @Nullable Object execute(@Nullable Object instance) throws ScriptException {
        if (instance == null) {
            throw new ScriptException("Cannot run null class/instance");
        }
        Map<String, Object> bindings = bindingsStore.getBindings(instance);

        // try to execute
        try {
            for (String methodName : Arrays.asList("eval", "main", "run")) {
                try {
                    Method evalMethod = instance.getClass().getMethod(methodName);
                    Parameter[] parameters = evalMethod.getParameters();
                    Object[] parameterValues = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        if (bindings == null) {
                            logger.error("Cannot found bindings data in store ! Does script execution took to long ?");
                            break;
                        }
                        String parameterKey = extractKey(parameters[i]);
                        if (parameterKey == null) {
                            logger.error("Cannot inject value in parameter {} for method {}", i, evalMethod.getName());
                            parameterValues[i] = null;
                            continue;
                        }
                        parameterValues[i] = getBindingValue(bindings, parameterKey);
                    }
                    return evalMethod.invoke(instance, parameterValues);
                } catch (NoSuchMethodException nse) {
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    String simpleName = instance.getClass().getSimpleName();
                    logger.error("Error executing entry point {} in {}", methodName, simpleName, e);
                    throw new ScriptException(
                            String.format("Error executing entry point %s in %s", methodName, simpleName, e));
                }
            }
            String simpleName = instance.getClass().getSimpleName();
            throw new ScriptException(
                    String.format("cannot execute: %s doesn't have an eval/main/run method", simpleName));
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    private @Nullable String extractKey(Parameter parameter) {
        InjectBinding injectBindingAnnotation = parameter.getAnnotation(InjectBinding.class);
        if (injectBindingAnnotation != null
                && !injectBindingAnnotation.named().equals(Java223Constants.ANNOTATION_DEFAULT)) {
            return injectBindingAnnotation.named();
        } else if (parameter.isNamePresent()) {
            return parameter.getName();
        }
        return null;
    }

    @Override
    public void associateBindings(Class<?> compiledClass, Object compiledInstance, Map<String, Object> bindings) {

        // adding a special shortcut : "bindings", to receive a map with all bindings
        bindings.put("bindings", bindings);

        // adding some custom additional fields
        bindings.putAll(additionalBindings);

        // storing bindings to be used as parameter in case of deferred execution
        bindingsStore.addBindings(compiledInstance, bindings);

        // statically injecting bindings data into other libraries if necessary
        try {
            for (String libraryClass : libraryClassList.values().stream().flatMap(List::stream).toList()) {
                logger.debug("Injecting bindings into library {} for {} to use it", libraryClass,
                        compiledClass.getName().toString());
                injectBinding(Class.forName(libraryClass, true, compiledClass.getClassLoader()), bindings);
            }
        } catch (ClassNotFoundException e1) {
            logger.error("Cannot initialize static libraries by injecting bindings into it. Should not happened ?!",
                    e1);
        }

        // finally, inject bindings data in the script
        injectBinding(compiledInstance, bindings);
    }

    private void injectBinding(Object objectToInject, Map<String, Object> bindings) {
        Object instance = null;
        Class<?> clazz = null;
        if (objectToInject instanceof Class<?> objetToInjectIsAStaticClass) {
            clazz = objetToInjectIsAStaticClass;
        } else {
            clazz = objectToInject.getClass();
            instance = objectToInject;
        }
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                InjectBinding injectAnnotation = field.getAnnotation(InjectBinding.class);
                if (injectAnnotation != null) {
                    String key;
                    if (!injectAnnotation.named().equals(Java223Constants.ANNOTATION_DEFAULT)) {
                        key = injectAnnotation.named();
                    } else {
                        key = field.getName();
                    }
                    Object valueToInject = bindings.get(key);
                    if (valueToInject != null) {
                        if (instance != null || Modifier.isStatic(field.getModifiers())) {
                            field.set(instance, valueToInject);
                        }
                    } else if (injectAnnotation.mandatory()) {
                        logger.error("There is no value with name {}. We cannot inject it in the class {}",
                                field.getName(), clazz.getSimpleName());
                    }
                }
            } catch (IllegalAccessException | IllegalArgumentException e) {
                logger.error("Cannot inject bindings {} into {}", field.getName(), clazz.getSimpleName(), e);
            }
        }
    }

    @Override
    public Map<String, Object> retrieveBindings(Class<?> compiledClass, Object compiledInstance) {
        return new HashMap<String, Object>();
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
