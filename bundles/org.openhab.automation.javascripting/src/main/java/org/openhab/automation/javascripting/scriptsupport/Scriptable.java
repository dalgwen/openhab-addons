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
package org.openhab.automation.javascripting.scriptsupport;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A script class that can be executed
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public interface Scriptable {

    @Nullable
    Object eval() throws Exception;

    void setBindings(Map<String, Object> mergedBindings);
}
