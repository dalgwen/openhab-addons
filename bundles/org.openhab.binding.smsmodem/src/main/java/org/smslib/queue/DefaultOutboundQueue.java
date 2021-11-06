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

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.smslib.helper.MessagePriorityComparator;
import org.smslib.message.OutboundMessage;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class DefaultOutboundQueue implements IOutboundQueue<OutboundMessage> {
    PriorityBlockingQueue<OutboundMessage> messageQueue = new PriorityBlockingQueue<>(1024,
            new MessagePriorityComparator());

    @Override
    public boolean start() throws Exception {
        return true;
    }

    @Override
    public boolean stop() throws Exception {
        return true;
    }

    @Override
    public boolean add(OutboundMessage o) throws Exception {
        return this.messageQueue.add(o);
    }

    @Override
    public OutboundMessage get() throws Exception {
        try {
            return this.messageQueue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    public OutboundMessage get(int count, TimeUnit timeUnit) throws Exception {
        try {
            return this.messageQueue.poll(count, timeUnit);
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    public int size() {
        return this.messageQueue.size();
    }
}
