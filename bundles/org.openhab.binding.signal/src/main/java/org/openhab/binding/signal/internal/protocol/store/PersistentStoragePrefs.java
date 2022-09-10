/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol.store;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Persistent storage backed by java prefs
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class PersistentStoragePrefs implements PersistentStorage {

    private static final String NODENAME = "org.openhab.binding.signal.account";

    private Preferences prefs;

    public PersistentStoragePrefs(String accountName) {
        this.prefs = Preferences.userRoot().node(NODENAME + accountName);
    }

    @Override
    public void save(StoreType store, String key, String value) {
        Preferences storeNode = prefs.node(store.name().toLowerCase());
        storeNode.put(key, value);
        try {
            storeNode.flush();
        } catch (BackingStoreException e) {
            throw new PersistenceException("Cannot save key in store " + store.name(), e);
        }
    }

    @Override
    public void delete(StoreType store, String key) {
        Preferences storeNode = prefs.node(store.name().toLowerCase());
        storeNode.remove(key);
        try {
            storeNode.flush();
        } catch (BackingStoreException e) {
            throw new PersistenceException("Cannot delete key in store  " + store.name(), e);
        }
    }

    @Override
    public @Nullable Map<String, String> load(StoreType store) {
        Map<String, String> loadedStore = new HashMap<>();
        Preferences storeNode = prefs.node(store.name().toLowerCase());
        try {
            for (String key : storeNode.keys()) {
                String value = storeNode.get(key, null);
                if (value != null) {
                    loadedStore.put(key, value);
                } else {
                    throw new PersistenceException("Value cannot be null " + storeNode.absolutePath() + "/" + key);
                }
            }
            return loadedStore;
        } catch (BackingStoreException e) {
            throw new PersistenceException("Cannot save key in store " + store.name(), e);
        }
    }

    @Override
    public void deleteStore(StoreType store) {
        Preferences storeNode = prefs.node(store.name().toLowerCase());
        try {
            storeNode.removeNode();
        } catch (BackingStoreException e) {
            throw new PersistenceException("Cannot delete node in store " + store.name(), e);
        }
    }

    @Override
    public void deleteEverything() {
        try {
            prefs.removeNode();
        } catch (BackingStoreException e) {
            throw new PersistenceException("Cannot clear storage", e);
        }
    }

    @Override
    public void save(String key, String value) {
        prefs.put(key, value);
    }

    @Override
    @Nullable
    public String get(String key) {
        String value = prefs.get(key, null);
        return value;
    }
}
