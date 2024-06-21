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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import javax.script.ScriptException;
import javax.tools.JavaFileObject;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.automation.java223.common.Java223Constants;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.automation.java223.common.RunScript;
import org.openhab.core.automation.module.script.ScriptExtensionManagerWrapper;
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
 * Multiple strategies sharing common goal : providing binding / execution / library to java223
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
        injectBindingsInto(bindings, compiledInstance, null);
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
                        Object[] parameterValues = getParameterValuesFor(method, bindingsStore.getBindings(instance),
                                null);
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

    private static Object[] getParameterValuesFor(Executable executable, Map<String, Object> bindings,
            Map<Class<?>, Object> libAlreadyInstanciatedLocal)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Parameter[] parameters = executable.getParameters();
        Object[] parameterValues = new Object[parameters.length];
        if (bindings == null && parameters.length > 0) {
            logger.error("Cannot found bindings data in store ! Does script preparation took too long ?");
            return null;
        }
        for (int i = 0; i < parameters.length; i++) {
            parameterValues[i] = extractBindingValueForElement(bindings, parameters[i], libAlreadyInstanciatedLocal);
        }
        return parameterValues;
    }

    /**
     * Inject bindings into objetToInject
     *
     * @param bindings a bindings maps with value to inject
     * @param objectToInject An object. Its fields will be filled with value from the
     *            bindings, if a match is found
     * @throws ScriptException
     */
    public static void injectBindingsInto(Map<String, Object> bindings, Object objectToInjectInto,
            @Nullable Map<Class<?>, Object> libAlreadyInstanciated) {

        Class<?> clazz = objectToInjectInto.getClass();

        for (Field field : getAllFields(clazz)) {
            try {
                Object valueToInject = extractBindingValueForElement(bindings, field, libAlreadyInstanciated);
                if (valueToInject != null) {
                    field.setAccessible(true);
                    field.set(objectToInjectInto, valueToInject);
                }
            } catch (IllegalAccessException | IllegalArgumentException | SecurityException | InstantiationException
                    | InvocationTargetException e) {
                logger.error("Cannot inject bindings or libs {} into {}", field.getName(), clazz.getSimpleName(), e);
            }
        }
    }

    /**
     * Search what to inject into an element.
     * Find a library, or compute a name to use as a key, then use this key to search a value in the bindings data
     *
     * @param bindings a map where to find the data to inject
     * @param annotatedElement the field/parameter element to inject value into
     * @param libAlreadyInstanciated A store of library instance, to avoid loop injection
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ScriptException
     **/
    @SuppressWarnings({ "null", "unused" })
    public static @Nullable Object extractBindingValueForElement(Map<String, Object> bindings,
            AnnotatedElement annotatedElement, @Nullable Map<Class<?>, Object> libAlreadyInstanciated)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        Map<Class<?>, Object> libAlreadyInstanciatedLocal = libAlreadyInstanciated == null ? new HashMap<>()
                : libAlreadyInstanciated;

        Class<?> fieldType;
        String codeName;
        if (annotatedElement instanceof Parameter parameter) {
            fieldType = parameter.getType();
            codeName = parameter.getName();
        } else if (annotatedElement instanceof Field field) {
            fieldType = field.getType();
            codeName = field.getName();
        } else {
            logger.warn(
                    "Cannot check target class for parameter. Only Parameter or Field accepted. We cannot inject it. Should not happened");
            return null;
        }

        // zero : exclusion case
        InjectBinding injectBindingAnnotation = annotatedElement.getAnnotation(InjectBinding.class);
        if (injectBindingAnnotation != null && !injectBindingAnnotation.enable()) {
            return null;
        }

        // first, special case, is the field a library ?
        if (librariesByFullClassName.get(fieldType.getName()) != null) { // it's a library
            InjectBinding libraryAnnotation = fieldType.getAnnotation(InjectBinding.class);
            if (libraryAnnotation != null && !libraryAnnotation.enable()) { // but it's disabled at class level
                // no injection
                return null;
            }
            // has it already been instantiated and stored in the store?
            Object valueToInject = libAlreadyInstanciatedLocal.get(fieldType);
            if (valueToInject == null) { // not instantiated, create it
                Constructor<?>[] constructors = fieldType.getDeclaredConstructors();
                // use the empty constructor if available, or the first one
                Constructor<?> constructor = Arrays.stream(constructors).filter(c -> c.getParameterCount() == 0)
                        .findFirst().orElseGet(() -> constructors[0]);
                Object[] parameterValues = getParameterValuesFor(constructor, bindings, libAlreadyInstanciatedLocal);
                valueToInject = constructor.newInstance(parameterValues);
                // and then also use injection into it
                injectBindingsInto(bindings, valueToInject, libAlreadyInstanciatedLocal);
                return valueToInject;
            }
        }

        // second. It's not a library, so search value in bindings map.
        // Choose a name to use as a key in the binding map
        // the name can be a path inside the object
        Queue<String> namePath = new LinkedList<>();
        String named;
        if (injectBindingAnnotation != null
                && !injectBindingAnnotation.named().equals(Java223Constants.ANNOTATION_DEFAULT)) {
            named = injectBindingAnnotation.named();
        } else {
            named = codeName;
        }
        Arrays.stream(named.split("\\.")).forEach(namePath::add);

        // third, choose where to look : in bindings, or deeper, in a preset :
        Object value = bindings;
        if (injectBindingAnnotation != null
                && !injectBindingAnnotation.preset().equals(Java223Constants.ANNOTATION_DEFAULT)) {
            ScriptExtensionManagerWrapper se = (ScriptExtensionManagerWrapper) bindings.get("scriptExtension");
            if (se != null) {
                Map<String, Object> presetMap = (Map<String, Object>) se.importPreset(injectBindingAnnotation.preset());
                if (presetMap != null) {
                    value = presetMap;
                } else {
                    logger.warn("Cannot find a preset named {} for the named parameter {}",
                            injectBindingAnnotation.preset(), named);
                }
            } else {
                logger.warn("Cannot find scriptExtension in bindings. Should not happen");
            }
        }

        // fourth, browse deep inside the object if there is a path to traverse
        while (!namePath.isEmpty()) {
            if (value instanceof Map<?, ?> elementToParseAsMap) {
                value = elementToParseAsMap.get(namePath.poll());
            } else {
                Field targetField;
                try {
                    String namePart = namePath.poll();
                    if (namePart != null) {
                        targetField = getFieldDeep(value.getClass(), namePart);
                        targetField.setAccessible(true);
                        value = targetField.get(value);
                    } else {
                        logger.debug("Cannot map a value to the path {}", named);
                        value = null;
                        break;
                    }
                } catch (NoSuchFieldException | SecurityException e) {
                    logger.debug("Cannot map a value to the path {}", named);
                    value = null;
                    break;
                }
            }
        }

        // fifth, check if it is mandatory
        if (value == null && injectBindingAnnotation != null && injectBindingAnnotation.mandatory()) {
            throw new Java223Exception("There is no value found for parameter/field named " + named
                    + ", but it is mandatory. We cannot inject it");
        } else if (value == null) {
            return null;
        }

        // six, check class compatibility
        if (!fieldType.isAssignableFrom(value.getClass())) {
            logger.warn("Parameter/field entry {} is of class {} and not assignable to type {}", named,
                    value.getClass().getName(), fieldType.getName());
        }
        return value;
    }

    private static Field getFieldDeep(Class<?> _clazz, String fieldName) throws NoSuchFieldException {
        Class<?> clazz = _clazz;
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException | SecurityException e) {
            }
            clazz = clazz.getSuperclass();
        }
        throw new NoSuchFieldException();
    }

    private static Set<Field> getAllFields(Class<?> type) {
        Set<Field> fields = new HashSet<Field>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
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
