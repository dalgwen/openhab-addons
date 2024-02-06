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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.automation.javascripting.annotations.Library;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.automation.javascripting.scriptsupport.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.bindings.BindingStrategy;

/**
 * @author Jürgen Weber - Initial contribution
 * @author Gwendal Roulleau - Adding library support
 */
public class BulkBindingStrategy implements BindingStrategy {

    private static Logger logger = LoggerFactory.getLogger(BulkBindingStrategy.class);

    private Map<String, Object> additionalBindings;
    private Map<String, List<String>> libraryClassList = new HashMap<>();

    public BulkBindingStrategy(Map<String, Object> additionalBindings) {
        super();
        this.additionalBindings = additionalBindings;
    }

    public void setLibraryClassList(String category, List<String> classList) {
        libraryClassList.put(category, classList);
    }

    @Override
    public void associateBindings(Class<?> compiledClass, Object compiledInstance, Map<String, Object> mergedBindings) {
        mergedBindings.putAll(additionalBindings);
        injectBinding(compiledInstance, mergedBindings);

        // initialize generated helper libraries by statically injecting bindings data into it
        try {
            for (String libraryClass : libraryClassList.values().stream().flatMap(List::stream).toList()) {
                logger.debug("Injecting bindings into library {} for the sake of {}", libraryClass,
                        compiledClass.getName().toString());
                Class.forName(libraryClass, true, compiledClass.getClassLoader()).getMethod("setBindings", Map.class)
                        .invoke(null, mergedBindings);
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException | ClassNotFoundException e1) {
            logger.error("Cannot initialize static libraries by injecting bindings into it. Should not happened ?!",
                    e1);
        }

        // create all annotated @Library field of this script and bind data into them
        for (Field field : compiledClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Library.class)) {
                try {
                    Object libraryInstance = field.getType().getDeclaredConstructor().newInstance();
                    injectBinding(libraryInstance, mergedBindings);
                    field.setAccessible(true);
                    field.set(compiledInstance, libraryInstance);
                } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException
                        | IllegalArgumentException | IllegalAccessException e) {
                    logger.error(
                            "Cannot inject library instance into {}. Do you have an empty constructor for the class {} ?",
                            field.getName(), field.getType().getName(), e);
                }
            }
        }
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
        if (instance instanceof Scriptable script) {
            script.setBindings(bindings);
        } else {
            try {
                clazz.getMethod("setBindings", Map.class).invoke(instance, bindings);
            } catch (NoSuchMethodException e) {
                logger.debug("Cannot inject bindings into script because there is no setBinding method");
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.error("Cannot inject bindings into script because there is an error with the setBinding method",
                        e);
            }
        }
    }

    @Override
    public Map<String, Object> retrieveBindings(Class<?> compiledClass, Object compiledInstance) {
        Map<String, Object> bindings = null;
        if (compiledInstance instanceof Script script) {
            bindings = script.getBindings();
        } else {
            bindings = new HashMap<String, Object>();
        }
        var bindingsFinal = bindings;
        // remove additionalBindings
        additionalBindings.keySet().stream().forEach(k -> bindingsFinal.remove(k));
        return bindings;
    }
}
