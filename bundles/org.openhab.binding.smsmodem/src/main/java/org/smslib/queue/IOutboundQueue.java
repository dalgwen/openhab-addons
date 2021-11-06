/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

package org.smslib.queue;

import java.util.concurrent.TimeUnit;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public interface IOutboundQueue<T> {
    public boolean start() throws Exception;

    public boolean stop() throws Exception;

    public boolean add(T o) throws Exception;

    public T get() throws Exception;

    public T get(int count, TimeUnit timeUnit) throws Exception;

    public int size();
}
