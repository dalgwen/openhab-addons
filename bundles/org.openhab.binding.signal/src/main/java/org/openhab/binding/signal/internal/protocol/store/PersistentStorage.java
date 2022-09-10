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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Provide functions to store to a persistent support
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public interface PersistentStorage {

    @Nullable
    Map<String, String> load(StoreType store);

    void delete(StoreType store, String key);

    void save(StoreType store, String key, String value);

    void save(String key, String value);

    @Nullable
    String get(String key);

    void deleteStore(StoreType store);

    void deleteEverything();

    default void deleteAllStores() {
        for (StoreType store : StoreType.values()) {
            deleteStore(store);
        }
    }
}
