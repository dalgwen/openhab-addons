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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.store.serializer.IdentityKeyAdapter;
import org.openhab.binding.signal.internal.protocol.store.serializer.IdentityKeyPairAdapter;
import org.openhab.binding.signal.internal.protocol.store.serializer.PreKeyRecordAdapter;
import org.openhab.binding.signal.internal.protocol.store.serializer.SessionRecordAdapter;
import org.openhab.binding.signal.internal.protocol.store.serializer.SignalProtocolAddressAdapter;
import org.openhab.binding.signal.internal.protocol.store.serializer.SignedPreKeyRecordAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.push.AccountIdentifier;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Expose protocol keys store as json string, to store them
 * with a persistent storage
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class JsonSignalStore implements SignalServiceDataStore, SignalServiceAccountDataStore {

    private final Logger logger = LoggerFactory.getLogger(JsonSignalStore.class);

    private final Gson gson;
    private final PersistentStorage persistentStorage;

    private final @Nullable IdentityKeyPair identityKeyPair;
    private final int registrationId;

    private Map<SignalProtocolAddress, IdentityKey> identities = new HashMap<>();
    private Map<Integer, PreKeyRecord> preKeys = new HashMap<>();
    private Map<Integer, SignedPreKeyRecord> signedPreKeys = new HashMap<>();
    private Map<SignalProtocolAddress, SessionRecord> sessions = new HashMap<>();
    private Map<SenderKeyIdentifier, SenderKeyRecord> senderKeys = new HashMap<>();
    private Map<String, HashSet<SignalProtocolAddress>> senderKeysSharedWith = new HashMap<>();

    public JsonSignalStore(@Nullable IdentityKeyPair identityKeyPair, int registrationId,
            PersistentStorage persistentStorage) {
        super();
        this.identityKeyPair = identityKeyPair;
        this.registrationId = registrationId;
        this.persistentStorage = persistentStorage;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(IdentityKeyPair.class, new IdentityKeyPairAdapter());
        gsonBuilder.registerTypeAdapter(IdentityKey.class, new IdentityKeyAdapter());
        gsonBuilder.registerTypeAdapter(PreKeyRecord.class, new PreKeyRecordAdapter());
        gsonBuilder.registerTypeAdapter(SignedPreKeyRecord.class, new SignedPreKeyRecordAdapter());
        gsonBuilder.registerTypeAdapter(SessionRecord.class, new SessionRecordAdapter());
        gsonBuilder.registerTypeAdapter(SignalProtocolAddress.class, new SignalProtocolAddressAdapter());
        gson = gsonBuilder.create();
    }

    @SuppressWarnings("unchecked")
    public boolean load() throws PersistenceException {
        logger.debug("Starting loading of signal store");
        for (StoreType store : StoreType.values()) {
            Map<String, String> mapLoaded = persistentStorage.load(store);
            if (mapLoaded == null) {
                continue;
            }
            @SuppressWarnings("rawtypes")
            Map mapToFill;
            Function<String, @Nullable Object> keyFunction;
            Function<String, @Nullable Object> valueFunction;
            switch (store) {
                case IDKEY_STORE:
                    mapToFill = identities;
                    keyFunction = key -> gson.fromJson(key, SignalProtocolAddress.class);
                    valueFunction = value -> gson.fromJson(value, IdentityKey.class);
                    break;
                case PREKEY_STORE:
                    mapToFill = preKeys;
                    keyFunction = key -> Integer.parseInt(key);
                    valueFunction = value -> gson.fromJson(value, PreKeyRecord.class);
                    break;
                case SENDERKEYSHAREDWITH_STORE:
                    mapToFill = senderKeysSharedWith;
                    keyFunction = key -> key;
                    Type senderKeySharedWithType = new TypeToken<HashSet<SignalProtocolAddress>>() {
                    }.getType();
                    valueFunction = value -> gson.fromJson(value, senderKeySharedWithType);
                    break;
                case SENDERKEY_STORE:
                    mapToFill = senderKeys;
                    keyFunction = key -> gson.fromJson(key, SenderKeyIdentifier.class);
                    valueFunction = value -> gson.fromJson(value, SenderKeyRecord.class);
                    break;
                case SESSION_STORE:
                    mapToFill = sessions;
                    keyFunction = key -> gson.fromJson(key, SignalProtocolAddress.class);
                    valueFunction = value -> gson.fromJson(value, SessionRecord.class);
                    break;
                case SIGNEDPREKEY_STORE:
                    mapToFill = signedPreKeys;
                    keyFunction = key -> Integer.parseInt(key);
                    valueFunction = value -> gson.fromJson(value, SignedPreKeyRecord.class);
                    break;
                default:
                    throw new IllegalArgumentException("this store cannot be loaded");
            }
            for (Entry<String, String> entry : mapLoaded.entrySet()) {
                Object key = keyFunction.apply(entry.getKey());
                Object value = valueFunction.apply(entry.getValue());
                if (key == null || value == null) {
                    throw new PersistenceException("Cannot load some key value pair ! (" + key + "," + value + ")");
                }
                mapToFill.put(key, value);
            }
        }

        if (preKeys.size() == 0) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    @Nullable
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return registrationId;
    }

    @Override
    public boolean saveIdentity(@Nullable SignalProtocolAddress address, @Nullable IdentityKey identityKey) {
        if (identityKey == null || address == null) {
            throw new IllegalArgumentException("SignalProtocolAddress or IdentityKey cannot be null");
        }
        boolean result = identities.put(address, identityKey) != null;
        persistentStorage.save(StoreType.IDKEY_STORE, gson.toJson(address), gson.toJson(identityKey));
        return result;
    }

    @Override
    public @Nullable IdentityKey getIdentity(@Nullable SignalProtocolAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("SignalProtocolAddress or IdentityKey cannot be null");
        }
        return identities.get(address);
    }

    @Override
    public @Nullable PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        return preKeys.get(preKeyId);
    }

    @Override
    public synchronized void storePreKey(int preKeyId, @Nullable PreKeyRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("PreKeyRecord cannot be null");
        }
        preKeys.put(preKeyId, record);
        persistentStorage.save(StoreType.PREKEY_STORE, Integer.toString(preKeyId), gson.toJson(record));
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return preKeys.containsKey(preKeyId);
    }

    @Override
    public synchronized void removePreKey(int preKeyId) {
        preKeys.remove(preKeyId);
        persistentStorage.delete(StoreType.PREKEY_STORE, Integer.toString(preKeyId));
    }

    @Override
    public synchronized void storeSession(@Nullable SignalProtocolAddress address, @Nullable SessionRecord record) {
        if (address == null || record == null) {
            throw new IllegalArgumentException("SignalProtocolAddress or SessionRecord cannot be null");
        }
        sessions.put(address, record);
        persistentStorage.save(StoreType.SESSION_STORE, gson.toJson(address), gson.toJson(record));
    }

    @Override
    public boolean containsSession(@Nullable SignalProtocolAddress address) {
        return sessions.containsKey(address);
    }

    @Override
    public synchronized void deleteSession(@Nullable SignalProtocolAddress address) {
        if (address != null) {
            sessions.remove(address);
            persistentStorage.delete(StoreType.SESSION_STORE, gson.toJson(address));
        }
    }

    @Override
    public @Nullable SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        return signedPreKeys.get(signedPreKeyId);
    }

    @Override
    @NonNullByDefault({})
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return new ArrayList<SignedPreKeyRecord>(signedPreKeys.values());
    }

    @Override
    public synchronized void storeSignedPreKey(int signedPreKeyId, @Nullable SignedPreKeyRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("SignedPreKeyRecord cannot be null");
        }
        signedPreKeys.put(signedPreKeyId, record);
        persistentStorage.save(StoreType.SIGNEDPREKEY_STORE, Integer.toString(signedPreKeyId), gson.toJson(record));
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return signedPreKeys.containsKey(signedPreKeyId);
    }

    @Override
    public synchronized void removeSignedPreKey(int signedPreKeyId) {
        signedPreKeys.remove(signedPreKeyId);
        persistentStorage.delete(StoreType.SIGNEDPREKEY_STORE, Integer.toString(signedPreKeyId));
    }

    @Override
    public boolean isTrustedIdentity(@Nullable SignalProtocolAddress name, @Nullable IdentityKey identityKey,
            IdentityKeyStore.@Nullable Direction direction) {
        IdentityKey storedIdentity = getIdentity(name);
        if (identityKey == null) {
            throw new IllegalStateException("Store not loaded");
        }
        return storedIdentity == null || identityKey.equals(storedIdentity);
    }

    @Override
    @NonNullByDefault({})
    public List<Integer> getSubDeviceSessions(String name) {
        List<Integer> ids = new ArrayList<Integer>();
        for (Entry<SignalProtocolAddress, SessionRecord> entry : sessions.entrySet()) {
            SignalProtocolAddress address = entry.getKey();
            if (address.getName().equals(name) && address.getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID) {
                ids.add(address.getDeviceId());
            }
        }
        return ids;
    }

    @Override
    public synchronized void deleteAllSessions(@Nullable String name) {
        for (Iterator<Entry<SignalProtocolAddress, SessionRecord>> it = sessions.entrySet().iterator(); it.hasNext();) {
            SignalProtocolAddress key = it.next().getKey();
            if (key.getName().equals(name)) {
                it.remove();
                persistentStorage.delete(StoreType.SESSION_STORE, gson.toJson(key));
            }
        }
    }

    @Override
    public SessionRecord loadSession(@Nullable SignalProtocolAddress address) {
        SessionRecord session = sessions.get(address);
        if (session == null) {
            session = new SessionRecord();
        }
        return session;
    }

    public void clearAllStores() {
        persistentStorage.deleteAllStores();
        this.identities = new HashMap<>();
        this.preKeys = new HashMap<>();
        this.signedPreKeys = new HashMap<>();
        this.sessions = new HashMap<>();
        this.senderKeys = new HashMap<>();
        this.senderKeysSharedWith = new HashMap<>();
    }

    @Override
    public void storeSenderKey(@Nullable SignalProtocolAddress sender, @Nullable UUID distributionId,
            @Nullable SenderKeyRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("SenderKeyRecord cannot be null");
        }
        SenderKeyIdentifier senderKeyId = new SenderKeyIdentifier(sender, distributionId);
        senderKeys.put(senderKeyId, record);
        persistentStorage.save(StoreType.SENDERKEY_STORE, gson.toJson(senderKeyId), gson.toJson(record));
    }

    @Override
    public @Nullable SenderKeyRecord loadSenderKey(@Nullable SignalProtocolAddress sender,
            @Nullable UUID distributionId) {
        SenderKeyRecord record = senderKeys.get(new SenderKeyIdentifier(sender, distributionId));
        return record;
    }

    @Override
    @NonNullByDefault({})
    public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses) throws NoSessionException {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public void archiveSession(@Nullable SignalProtocolAddress address) {
        SessionRecord loadSession = loadSession(address);
        loadSession.archiveCurrentState();
        storeSession(address, loadSession);
        if (address != null) {
            clearSenderKeySharedWith(List.of(address));
        }
    }

    @Override
    @NonNullByDefault({})
    public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    @NonNullByDefault({})
    public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
        HashSet<SignalProtocolAddress> signalAddresses = senderKeysSharedWith.get(distributionId.asUuid().toString());
        if (signalAddresses != null) {
            return signalAddresses;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    @NonNullByDefault({})
    public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
        String uuidAsString = distributionId.asUuid().toString();
        HashSet<SignalProtocolAddress> senderKeysForThisDistribution = senderKeysSharedWith.getOrDefault(uuidAsString,
                new HashSet<SignalProtocolAddress>());
        senderKeysForThisDistribution.addAll(addresses);
        senderKeysSharedWith.put(uuidAsString, senderKeysForThisDistribution);
        persistentStorage.save(StoreType.SENDERKEYSHAREDWITH_STORE, uuidAsString,
                gson.toJson(senderKeysForThisDistribution));
    }

    @Override
    @NonNullByDefault({})
    public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses) {
        for (Entry<String, HashSet<SignalProtocolAddress>> entry : senderKeysSharedWith.entrySet()) {
            boolean changed = entry.getValue().removeAll(addresses);
            if (changed) {
                persistentStorage.save(StoreType.SENDERKEYSHAREDWITH_STORE, entry.getKey(),
                        gson.toJson(entry.getValue()));
            }
        }
    }

    @Override
    public SignalServiceAccountDataStore get(@Nullable AccountIdentifier accountIdentifier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SignalServiceAccountDataStore aci() {
        return this;
    }

    @Override
    public SignalServiceAccountDataStore pni() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMultiDevice() {
        return false;
    }
}
