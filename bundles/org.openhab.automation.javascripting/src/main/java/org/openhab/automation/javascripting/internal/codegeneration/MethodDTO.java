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
package org.openhab.automation.javascripting.internal.codegeneration;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A DTO passing method data to the veolicity template
 *
 * @author Gwendal Roulleau - Refactor using velocity
 */
@NonNullByDefault
public record MethodDTO(String returnValueType, String name, List<String> parameterTypes) {

}
