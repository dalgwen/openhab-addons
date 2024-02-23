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
package org.openhab.transform.timedevent.profiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helps managing only one thread scheduling per channel, and not per profile
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class EventManager {

    private Map<Object, Set<TimedEventProfile>> profilesByChannel = new HashMap<>();

    private Map<Object, TimedEventProfile> currentResponsibleProfilesByChannel = new HashMap<>();

}
