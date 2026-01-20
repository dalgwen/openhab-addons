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

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to account-related operation
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SignalAccount {

    private final Logger logger = LoggerFactory.getLogger(SignalAccount.class);

    private final String phoneNumber;
    private final String deviceName;

    private final SignalAccountManager signalAccountManager;
    private final SignalAccountEventListener signalAccountEventListener;

    private final ProvisionType provisionType;

    public SignalAccount(SignalAccountEventListener signalAccountEventListener, String phoneNumber,
                         String deviceName, SignalAccountManager signalAccountManager, ProvisionType provisionType) {
        this.signalAccountEventListener = signalAccountEventListener;
        this.phoneNumber = phoneNumber;
        this.deviceName = deviceName;
        this.signalAccountManager = signalAccountManager;
        this.provisionType = provisionType;
    }

    public void check() throws IncompleteRegistrationException, IOException {
        synchronized (this) {
            try {
                boolean exists = signalAccountManager.getSignalService().exists(phoneNumber);
                if (!exists) { // try to register
                    newStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, "Account not registered");
                    throw new IncompleteRegistrationException(RegistrationState.REGISTER_NEEDED, "Account not registered");
                }
                newStateEvent(SignalAccountEventListener.ConnectionState.CONNECTED, null);
            } catch (JsonResponseException e) {
                throw new IOException(e);
            }
        }
    }

    public void updateProfile() throws IOException {
        try {
            if (provisionType == ProvisionType.MAIN) {
                Map<String, Object> config = new HashMap<>();
                config.put("avatar", encodeOpenHABAvatarAsRFC2397());
                config.put("givenName", deviceName);
                signalAccountManager.getSignalService().sendRequest(phoneNumber, "updateProfile", config, Object.class);
            }
        } catch (IOException | JsonResponseException e) {
            throw new IOException("Cannot update signal profile for "  + phoneNumber + " to set name " + deviceName + ". Cause : " + e.getMessage());
        }
    }

    private static String encodeOpenHABAvatarAsRFC2397() throws IOException {
        try (InputStream resource = SignalAccount.class.getResourceAsStream("/openhabavatar.png")) {
            if (resource == null) {
                throw new IllegalStateException("Avatar is not there, shouldn't happen !");
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(resource.readAllBytes());
        }
    }

    public String registerLinked() throws IncompleteRegistrationException, IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("name", deviceName);
        try {
            JsonResponse<Object> response = signalAccountManager.getSignalService().sendRequest(phoneNumber, "startLink", config, Object.class);
            @SuppressWarnings("unchecked")
            Map<String,String> result = (Map<String,String>) response.getResult();
            return Optional.ofNullable(result).map(map -> map.get("deviceLinkUri"))
                    .orElseThrow(() -> new IncompleteRegistrationException(RegistrationState.ERROR, "No deviceLinkUri returned"));
        } catch (JsonResponseException | ClassCastException e) {
            logger.debug("Error JsonResponseException during linked registration attempt", e);
            throw new IncompleteRegistrationException(RegistrationState.ERROR, e.getMessage());
        }
    }

    public void finishLink(String deviceLinkUri) throws IOException, IncompleteRegistrationException {
        Map<String, Object> config = new HashMap<>();
        config.put("deviceLinkUri", deviceLinkUri);
        config.put("deviceName", deviceName);
        try {
            JsonResponse<Object> response = signalAccountManager.getSignalService().sendRequest(null, "finishLink", config, Object.class);
            Map<String,String> result = (Map<String,String>) response.getResult();
            Optional.ofNullable(result).map(map -> map.get("number"))
                    .orElseThrow(() -> new IncompleteRegistrationException(RegistrationState.ERROR, "No number returned from the finish link attempt"));
            logger.debug("finishLink response: {}", response);
        } catch (JsonResponseException | ClassCastException e) {
            logger.debug("Error JsonResponseException during linked registration attempt", e);
            throw new IncompleteRegistrationException(RegistrationState.ERROR, e.getMessage());
        }
    }

    public void register(String captcha, RegistrationType verificationCodeMethod) throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("captcha", captcha);
        config.put("reregister", true);
        config.put("voice", false);
        try {
            if (verificationCodeMethod == RegistrationType.PhoneCall) {
                // first ask SMS
                try {
                    JsonResponse<Object> response = signalAccountManager.getSignalService().sendRequest(phoneNumber, "register", config, Object.class);
                } catch (JsonResponseException e) {
                    if (! e.getMessage().contains("InvalidTransportModeException")) {
                        //ignore InvalidTransportModeException (it may be an old account ans sms is disabled server side) and try directly phone call
                        throw e;
                    }
                }
                newStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, "Waiting mandatory 60 seconds before doing voice registration phone call");
                // wait 60+ seconds
                Thread.sleep(62000);
                // retry with voice
                config.put("voice", true);
                signalAccountManager.getSignalService().sendRequest(phoneNumber, "register", config, Object.class);
                newStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, "Waiting for verification code (sent by voice call)");
            } else {
                JsonResponse<Object> response = signalAccountManager.getSignalService().sendRequest(phoneNumber, "register", config, Object.class);
                newStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, "Waiting for verification code (sent by SMS)");
            }
        } catch (JsonResponseException | InterruptedException e) {
            newStateEvent(SignalAccountEventListener.ConnectionState.DISCONNECTED, "Cannot register:" + e.getMessage());
            throw new IOException("Cannot register", e);
        }
    }

    public void verify(String verificationCode) throws IncompleteRegistrationException, IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("verificationCode", verificationCode);
        try {
            signalAccountManager.getSignalService().sendRequest(phoneNumber, "verify", config, Object.class);
            check();
            updateProfile();
        } catch (JsonResponseException e) {
            logger.debug("Error JsonResponseException during registration/verify attempt", e);
            throw new IncompleteRegistrationException(RegistrationState.ERROR, e.getMessage());
        }
    }

    public DeliveryReport send(String address, String message, @Nullable String attachment) {
        return signalAccountManager.getSignalService().send(phoneNumber, address, message, attachment);
    }

    public synchronized void deleteAccount() throws IOException {
        try {
            // is it the right way to unregister? Probably not
            switch (provisionType) {
                case MAIN -> signalAccountManager.getSignalService().sendRequest(phoneNumber, "unregister", null, Object.class);
                case LINKED -> signalAccountManager.getSignalService().sendRequest(phoneNumber, "delete", null, Object.class);
            }

        } catch (JsonResponseException e) {
            throw new RuntimeException(e);
        }
    }

    public void newStateEvent(SignalAccountEventListener.ConnectionState connectionState, @Nullable String detailedMessage) {
        signalAccountEventListener.newStateEvent(connectionState, detailedMessage);
    }

    public void messageReceived(RecipientAddress recipientAddress, String message) {
        signalAccountEventListener.messageReceived(recipientAddress, message);
    }

    public void reactionReceived(RecipientAddress recipientAddress, Reaction reaction) {
        signalAccountEventListener.reactionReceived(recipientAddress, reaction);
    }

    public void deliveryStatusReceived(DeliveryReport deliveryReport) {
        signalAccountEventListener.deliveryStatusReceived(deliveryReport);
    }
}
