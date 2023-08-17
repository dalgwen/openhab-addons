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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.openhab.automation.javascripting.scriptsupport.Script;

import ch.obermuhlner.scriptengine.java.bindings.BindingStrategy;

/**
 * @author Jürgen Weber - Initial contribution
 */

public class BulkBindingStrategy implements BindingStrategy {

    @Override
    public void associateBindings(Class<?> compiledClass, Object compiledInstance, Map<String, Object> mergedBindings) {
        Method m;
        try {
            if (compiledInstance instanceof Script) {
                m = compiledClass.getMethod("setBindings", Map.class);

                m.invoke(compiledInstance, mergedBindings);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Object> retrieveBindings(Class<?> compiledClass, Object compiledInstance) {
        Method m;

        Map<String, Object> bindings = null;

        try {
            if (compiledInstance instanceof Script) {

                m = compiledClass.getMethod("getBindings");

                Object o = m.invoke(compiledInstance);

                bindings = (Map<String, Object>) o;
            } else {
                bindings = new HashMap<String, Object>();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return bindings;
    }
}
