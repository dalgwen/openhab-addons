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

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public abstract class ModemSchedulerTask {
    protected long lastExecuted = 0;
    protected long interval = 0;

    protected final Modem modem;

    public ModemSchedulerTask(Modem modem, long interval) {
        this.modem = modem;
        this.interval = interval;
    }

    public void tryExecute() throws Exception {
        long now = System.currentTimeMillis();
        if (lastExecuted + interval < now) {
            this.lastExecuted = now;
            execute();
        }
    }

    protected abstract void execute() throws Exception;
}
