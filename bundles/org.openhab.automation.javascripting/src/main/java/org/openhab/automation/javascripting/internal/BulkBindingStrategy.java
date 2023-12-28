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
import java.util.Map;

import org.openhab.automation.javascripting.annotations.Library;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.obermuhlner.scriptengine.java.bindings.BindingStrategy;

/**
 * @author Jürgen Weber - Initial contribution
 */

public class BulkBindingStrategy implements BindingStrategy {

    private static Logger logger = LoggerFactory.getLogger(BulkBindingStrategy.class);

    @Override
    public void associateBindings(Class<?> compiledClass, Object compiledInstance, Map<String, Object> mergedBindings) {
        if (compiledInstance instanceof Script script) {
            script.setBindings(mergedBindings);
            script.makeShortcuts();
        }

        // create, bind data into libraries and inject libraries into script
        for (Field field : compiledClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Library.class)) {
                try {
                    Object libraryInstance = field.getType().getDeclaredConstructor().newInstance();
                    if (libraryInstance instanceof Script scriptLibrary) {
                        scriptLibrary.setBindings(mergedBindings);
                        scriptLibrary.makeShortcuts();
                    }
                    field.setAccessible(true);
                    field.set(compiledInstance, libraryInstance);
                } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException
                        | IllegalArgumentException | IllegalAccessException e) {
                    logger.error("Cannot inject library instance into {}. Do you have an empty constructor ?",
                            field.getName(), e);
                }
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
        return bindings;
    }
}
