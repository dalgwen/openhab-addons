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
package org.openhab.automation.java223.common;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.automation.java223.internal.strategy.Java223Strategy;

/**
 * A wrapper (delegate pattern) for injecting into script
 * This wrapper is visible to the helper library.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class BindingInjector {

    public static void injectBindingsInto(Map<String, Object> bindings, Object objectToInjectInto) {
        Java223Strategy.injectBindingsInto(bindings, objectToInjectInto, null);
    }

    public static @Nullable Object extractBindingValueForElement(Map<String, Object> bindings,
            AnnotatedElement annotatedElement) {
        return Java223Strategy.extractBindingValueForElement(bindings, annotatedElement);
    }
}
