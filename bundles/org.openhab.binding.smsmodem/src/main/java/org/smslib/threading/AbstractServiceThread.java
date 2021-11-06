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

package org.smslib.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public abstract class AbstractServiceThread extends Thread {
    static Logger logger = LoggerFactory.getLogger(AbstractServiceThread.class);

    int delay;

    int initialDelay;

    boolean enabled;

    boolean cancelled;

    public AbstractServiceThread(String name, int delay, int initialDelay, boolean enabled) {
        setName(name);
        setDelay(delay);
        setInitialDelay(initialDelay);
        if (enabled) {
            enable();
        } else {
            disable();
        }
        this.cancelled = false;
    }

    public int getDelay() {
        return this.delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getInitialDelay() {
        return this.initialDelay;
    }

    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void enable() {
        logger.debug("Enabling " + getName() + "...");
        this.enabled = true;
    }

    public void disable() {
        logger.debug("Disabling " + getName() + "...");
        this.enabled = false;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void cancel() {
        gracefulTerminate();
        disable();
        this.cancelled = true;
        interrupt();
        try {
            this.join();
        } catch (InterruptedException e) {
            // Ignore this.
        }
    }

    @Override
    public void run() {
        try {
            sleep(getInitialDelay());
        } catch (InterruptedException e) {
            if (!isCancelled()) {
                logger.error("Interrupted during initial delay!", e);
            }
        }
        while (!isCancelled()) {
            try {
                sleep(getDelay());
            } catch (InterruptedException e) {
                if (!isCancelled()) {
                    logger.error("Interrupted during interval delay!", e);
                }
            }
            try {
                if (isEnabled() && !isCancelled()) {
                    process();
                }
            } catch (InterruptedException e) {
                if (isCancelled()) {
                    logger.debug("Stopped.");
                    break;
                }
                logger.debug("Interrupted!");
            } catch (Exception e) {
                logger.error("Unhandled exception!", e);
            }
        }
    }

    public abstract void process() throws Exception;

    public abstract void gracefulTerminate();
}
