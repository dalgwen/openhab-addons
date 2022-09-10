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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.IncompleteRegistrationException;
import org.openhab.binding.signal.internal.protocol.RegistrationState;
import org.openhab.binding.signal.internal.protocol.StateListener;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceAccountManager.NewDeviceRegistrationReturn;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

/**
 * A context storing data and parameter, for a linked account
 * Also manage registration, with a QR code
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class ContextLinkedAccount extends Context {

    private static final String KEY_E64NUMBER = "e64number";
    private String deviceName;

    public ContextLinkedAccount(PersistentStorage persistentStorage, String deviceName) {
        super(persistentStorage);
        this.deviceName = deviceName;
        loadStore();
    }

    @Override
    @Nullable
    public String getE164() {
        return persistentStorage.get(KEY_E64NUMBER);
    }

    public void setE164(String number) {
        persistentStorage.save(KEY_E64NUMBER, number);
    }

    @Override
    public RegistrationState getRegistrationState() {
        if (getAci() == null || !isPresent(getE164())) {
            return RegistrationState.QR_CODE_NEEDED;
        } else {
            return RegistrationState.REGISTERED;
        }
    }

    @Override
    public void verification() throws IOException, IncompleteRegistrationException {
        // Nothing, there is no verification code with a linked account
    }

    @Override
    public void register(@Nullable StateListener connectionStateListener)
            throws IOException, InvalidInputException, IncompleteRegistrationException {
        try {
            // we have to create another accountManager for this operation (I don't know why!)
            SignalServiceAccountManager accountManager = new SignalServiceAccountManager(getConfig(),
                    new DynamicCredentialsProvider(null, null, getPassword(), SignalServiceAddress.DEFAULT_DEVICE_ID),
                    Context.USER_AGENT, new GroupsV2Operations(ClientZkOperations.create(getConfig())), false);

            var deviceUuid = accountManager.getNewDeviceUuid();
            final String deviceKeyString = Base64.getEncoder()
                    .encodeToString(getIdentityKeyPair().getPublicKey().getPublicKey().serialize()).replace("=", "");
            URI linkURI = new URI("sgnl://linkdevice?uuid=" + URLEncoder.encode(deviceUuid, StandardCharsets.UTF_8)
                    + "&pub_key=" + URLEncoder.encode(deviceKeyString, StandardCharsets.UTF_8));
            if (connectionStateListener != null) {
                connectionStateListener.qrCodeToScan(linkURI.toString());
            } else {
                throw new IncompleteRegistrationException(RegistrationState.QR_CODE_NEEDED,
                        "Cannot send QR code to the calling service");
            }
            NewDeviceRegistrationReturn ret = accountManager.getNewDeviceRegistration(getIdentityKeyPair());
            logger.info("Received link information from {}, linking in progress ...", ret.getNumber());
            String deviceNameToRegister = isPresent(deviceName) ? this.deviceName : "openHAB-" + ret.getNumber();
            String encryptedDeviceName = DeviceNameUtil.encryptDeviceName(deviceNameToRegister,
                    ret.getIdentity().getPrivateKey());
            Integer deviceId = accountManager.finishNewDeviceRegistration(ret.getProvisioningCode(), false, true,
                    getOrCreateRegistrationId(), encryptedDeviceName);
            setE164(ret.getNumber());
            setACI(ret.getAci().toString());
            setDeviceId(deviceId);
            setIdentityKeyPair(ret.getIdentity());
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        } catch (TimeoutException e) {
            throw new IOException("Cannot get response from service", e);
        }
    }

    @Override
    public String getId() {
        return "linked-" + getE164();
    }
}
