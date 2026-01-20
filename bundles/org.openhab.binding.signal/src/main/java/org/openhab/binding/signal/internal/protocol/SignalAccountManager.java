/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.SignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manage accounts and expose a singleton Signal service implementation instance.
 * Listen to messages and route them to the right signal accounts.
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalAccountManager {

    private final Logger logger = LoggerFactory.getLogger(SignalAccountManager.class);

    private final Map<String, SignalAccount> signalAccounts = new ConcurrentHashMap<>();
    @Nullable
    private SignalService signalService;

    private final Lock lock = new ReentrantLock();

    public SignalAccountManager() {}

    public void setSignalService(SignalService signalService) {
        @Nullable SignalService signalServiceLocal = this.signalService;
        if (signalServiceLocal != null) {
            signalServiceLocal.stop();
        }
        this.signalService = signalService;
        if (!signalAccounts.isEmpty()) {
            signalService.start();
        }
    }

    public SignalService getSignalService() {
        SignalService signalServiceLocal = signalService;
        if (signalServiceLocal == null) {
            throw new IllegalStateException("Signal service not set. Should not happen.");
        }
        return signalServiceLocal;
    }

    public Iterable<SignalAccount> getAllAccounts() {
        return signalAccounts.values();
    }

    public SignalAccount getSignalAccount(SignalAccountEventListener signalAccountEventListener, String phoneNumber, String deviceName, ProvisionType provisionType) {
        lock.lock();
        try {
            boolean shouldStartBecauseEmpty = signalAccounts.isEmpty();
            SignalAccount signalAccount = signalAccounts.computeIfAbsent(phoneNumber, (number) -> new SignalAccount(signalAccountEventListener, phoneNumber, deviceName, this, provisionType));
            if (shouldStartBecauseEmpty) {
                logger.debug("First SignalAccount start, connecting to signal service...");
                getSignalService().start();
            }
            if (signalAccount != null) { // never null, but checkstyle complains
                return signalAccount;
            }
            throw new IllegalStateException("Cannot create SignalAccount for phone number " + phoneNumber); // never happen
        } finally {
            lock.unlock();
        }
    }

    public @Nullable SignalAccount getSignalAccount(String phoneNumber) {
        return signalAccounts.get(phoneNumber);
    }

    public void removeSignalAccount(String phoneNumber) {
        lock.lock();
        try {
            signalAccounts.remove(phoneNumber);
            if (signalAccounts.isEmpty()) {
                getSignalService().stop();
            }
        } finally {
            lock.unlock();
        }
    }
}
