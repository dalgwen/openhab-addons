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

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.RegistrationState;
import org.openhab.binding.signal.internal.protocol.StateListener;
import org.openhab.binding.signal.internal.protocol.Utils;
import org.signal.zkgroup.InvalidInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalCdshUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;
import org.whispersystems.signalservice.internal.util.Util;

/**
 * Store and expose all mandatory information to run the Signal service
 * Manage registration
 * (Signal is a statefull service)
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public abstract class Context implements CredentialsProvider {

    protected final Logger logger = LoggerFactory.getLogger(Context.class);

    public static final String MR_ENCLAVE = "c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15";
    public static final byte[] UNIDENTIFIED_SENDER_TRUST_ROOT = Base64.getDecoder()
            .decode("BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF");
    public static final String SIGNAL_URL = "https://textsecure-service.whispersystems.org";
    private static final String SIGNAL_CDN_URL = "https://cdn.signal.org";
    private static final String SIGNAL_CDN2_URL = "https://cdn2.signal.org";
    protected static final String SIGNAL_CAPTCHA_SCHEME = "signalcaptcha://";
    private static final String SIGNAL_CONTACT_DISCOVERY_URL = "https://api.directory.signal.org";
    private static final String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
    private static final String SIGNAL_STORAGE_URL = "https://storage.signal.org";
    private static final String SIGNAL_CDSH_URL = "";
    public static final String USER_AGENT = "Signal-Android/5.51.7 signal-cli";
    public static final int PREKEY_MINIMUM_SIZE = 5;
    public static final int PREKEY_BATCH_SIZE = 20;

    public static final TrustStore TRUST_STORE = new WhisperTrustStore("whisper.store");

    private static final String KEY_UUID = "uuid";
    private static final String KEY_REGISTRATION_ID = "registrationid";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_IDKEY = "idkey";
    private static final String KEY_DEVICEID = "deviceid";

    protected PersistentStorage persistentStorage;

    protected JsonSignalStore jsonSignalStore;

    @Nullable
    protected SignalServiceAccountManager accountManager;

    protected static SignalServiceConfiguration config = new SignalServiceConfiguration(
            new SignalServiceUrl[] { new SignalServiceUrl(SIGNAL_URL, TRUST_STORE) },
            Map.of(0, new SignalCdnUrl[] { new SignalCdnUrl(SIGNAL_CDN_URL, TRUST_STORE) }, 2,
                    new SignalCdnUrl[] { new SignalCdnUrl(SIGNAL_CDN2_URL, TRUST_STORE) }),
            new SignalContactDiscoveryUrl[] {
                    new SignalContactDiscoveryUrl(SIGNAL_CONTACT_DISCOVERY_URL, TRUST_STORE) },
            new SignalKeyBackupServiceUrl[] { new SignalKeyBackupServiceUrl(SIGNAL_KEY_BACKUP_URL, TRUST_STORE) },
            new SignalStorageUrl[] { new SignalStorageUrl(SIGNAL_STORAGE_URL, TRUST_STORE) },
            new SignalCdshUrl[] { new SignalCdshUrl(SIGNAL_CDSH_URL, TRUST_STORE) },
            List.of(chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())),
            Optional.absent(), Optional.absent(), Base64.getDecoder().decode(
                    "AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY/JYJHRooo5CEqYKBqdFnmbTVGEkCvJKxLnjwKWf+fEPoWeQFj5ObDjcKMZf2Jm2Ae69x+ikU5gBXsRmoF94GXQ=="));;

    /**
     * If the store should be recreated (keys changed)
     */
    private boolean needsKeysRecreation = false;

    private @Nullable IdentityKeyPair identityKeyPair;

    public Context(PersistentStorage persistentStorage) {
        this.persistentStorage = persistentStorage;
        this.jsonSignalStore = new JsonSignalStore(getIdentityKeyPair(), getOrCreateRegistrationId(),
                persistentStorage);
    }

    public void loadStore() {
        try {
            boolean loadOk = jsonSignalStore.load();
            if (!loadOk) { // missing keys ? Depleted ?
                setNeedsKeysRecreation(true); // mark the store for key recreation
            }
        } catch (PersistenceException e) { // store is broken, it is simpler to delete and recreate
            // this will force a key recreation :
            setNeedsKeysRecreation(true);
            // delete all stored information :
            persistentStorage.deleteAllStores();
            // recreate a store :
            this.jsonSignalStore = new JsonSignalStore(getIdentityKeyPair(), getOrCreateRegistrationId(),
                    persistentStorage);
        }
    }

    @Override
    public @Nullable abstract String getE164();

    public abstract RegistrationState getRegistrationState();

    public abstract void verification() throws IOException, IncompleteRegistrationException;

    public abstract void register(@Nullable StateListener connectionStateListener)
            throws InvalidInputException, IncompleteRegistrationException, IOException;

    protected boolean isPresent(@Nullable String stringToTest) {
        return stringToTest != null && !stringToTest.isBlank();
    }

    /**
     * An unique id to recognize the account
     *
     * @return The id
     */
    public abstract String getId();

    public SignalServiceAccountManager getOrCreateAccountManager() throws IncompleteRegistrationException {
        return getOrCreate(() -> accountManager, () -> accountManager = new SignalServiceAccountManager(config, this,
                Context.USER_AGENT, new GroupsV2Operations(ClientZkOperations.create(config)), false));
    }

    public SignalServiceConfiguration getConfig() {
        return config;
    }

    @Override
    public String getPassword() {
        return getInStoreOrCreate(KEY_PASSWORD, () -> Base64.getEncoder().encodeToString(Util.getSecretBytes(18)));
    }

    public Integer getOrCreateRegistrationId() {
        return Integer.parseInt(getInStoreOrCreate(KEY_REGISTRATION_ID,
                () -> Integer.toString(KeyHelper.generateRegistrationId(false))));
    }

    @Override
    public int getDeviceId() {
        return Integer.parseInt(
                getInStoreOrCreate(KEY_DEVICEID, () -> Integer.toString(SignalServiceAddress.DEFAULT_DEVICE_ID)));
    }

    public void setDeviceId(Integer deviceId) {
        persistentStorage.save(KEY_DEVICEID, deviceId.toString());
    }

    @Override
    public @Nullable ACI getAci() {
        String aciString = this.persistentStorage.get(KEY_UUID);
        return aciString == null ? null : ACI.parseOrThrow(aciString);
    }

    public void setACI(String aci) {
        persistentStorage.save(KEY_UUID, aci);
    }

    public IdentityKeyPair getIdentityKeyPair() {
        return getOrCreate(() -> identityKeyPair, () -> {
            String identityKeyPairBase64 = getInStoreOrCreate(KEY_IDKEY, () -> {
                logger.info("Creating new identity key pair");
                setNeedsKeysRecreation(true);
                return Base64.getEncoder().encodeToString(Utils.createIdentityKeyPair().serialize());
            });
            identityKeyPair = new IdentityKeyPair(Base64.getDecoder().decode(identityKeyPairBase64));
        });
    }

    public void setIdentityKeyPair(IdentityKeyPair identity) {
        this.identityKeyPair = identity;
        setNeedsKeysRecreation(true);
        persistentStorage.save(KEY_IDKEY, Base64.getEncoder().encodeToString(identity.serialize()));
    }

    public SignalServiceAddress getSignalServiceAddress() {
        ACI aci = getAci();
        if (aci == null) {
            throw new IllegalStateException("Can't compute address with no ACI");
        }
        return new SignalServiceAddress(aci, this.getE164());
    }

    public JsonSignalStore getProtocolStore() {
        return jsonSignalStore;
    }

    public boolean needsKeysRecreation() {
        return needsKeysRecreation;
    }

    public void setNeedsKeysRecreation(boolean b) {
        this.needsKeysRecreation = b;
    }

    private <T> T getOrCreate(Supplier<T> supplier, Runnable creator) {
        var value = supplier.get();
        if (value != null) {
            return value;
        }

        synchronized (this) {
            value = supplier.get();
            if (value != null) {
                return value;
            }
            creator.run();
            return supplier.get();
        }
    }

    private String getInStoreOrCreate(String key, Supplier<String> supplier) {
        return getOrCreate(() -> persistentStorage.get(key), () -> persistentStorage.save(key, supplier.get()));
    }
}
