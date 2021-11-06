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

package org.smslib.gateway.modem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.gateway.AbstractGateway.Status;
import org.smslib.helper.Common;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class ModemScheduler extends Thread {
    static Logger logger = LoggerFactory.getLogger(ModemScheduler.class);

    Modem modem;

    List<ModemSchedulerTask> scheduledTasks = new ArrayList<>();

    boolean shouldCancel = false;

    Lock lock = new ReentrantLock();

    public ModemScheduler(Modem modem) {
        this.modem = modem;
    }

    public void cancel() {
        logger.debug("Cancelling!");
        this.shouldCancel = true;
    }

    public void addTask(ModemSchedulerTask task) {
        this.lock.lock();
        try {
            scheduledTasks.add(task);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void run() {
        logger.debug("Started!");
        while (!this.shouldCancel) {
            if (this.modem.getStatus() == Status.Started) {
                try {
                    this.modem.getModemDriver().lock.lock();
                    try {
                        this.lock.lock();
                        try {
                            for (ModemSchedulerTask t : scheduledTasks) {
                                t.tryExecute();
                            }
                        } finally {
                            this.lock.unlock();
                        }
                    } finally {
                        this.modem.getModemDriver().lock.unlock();
                    }
                } catch (Exception e) {
                    logger.error("Exception running modem scheduled task", e);
                    this.cancel();
                    new Thread(() -> modem.error()).start();
                }
            }
            if (!this.shouldCancel) {
                Common.countSheeps(5000);
            }
        }
        logger.debug("Stopped!");
    }
}
