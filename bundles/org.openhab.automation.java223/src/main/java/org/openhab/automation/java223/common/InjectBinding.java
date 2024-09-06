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

import static org.openhab.automation.java223.common.Java223Constants.ANNOTATION_DEFAULT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link InjectBinding} tags field with an injection intent and related details.
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE })
@NonNullByDefault
public @interface InjectBinding {

    /**
     * Prevent injection. Useful if you want to do it yourself.
     *
     * @return
     */
    public boolean enable() default true;

    /**
     * If set, use this name. Else use the variable name.
     *
     * @return
     */
    public String named() default ANNOTATION_DEFAULT;

    /**
     * If set, instructs the framework to get value inside a preset
     *
     * @return
     */
    public String preset() default ANNOTATION_DEFAULT;

    /**
     * If true and no value to inject is found, the binding process will fail with an exception.
     * If false, null value are allowed.
     *
     * @return
     */
    public boolean mandatory() default true;
}
