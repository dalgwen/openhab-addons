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
package org.openhab.binding.signal.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.signal.internal.protocol.*;
import org.openhab.binding.signal.internal.protocol.service.JsonRpcStdioLocalService;
import org.openhab.binding.signal.internal.protocol.service.JsonRpcStdioManagedService;
import org.openhab.binding.signal.internal.protocol.service.JsonRpcTcpService;
import org.openhab.core.OpenHAB;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Main interface for all signal-related services
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public abstract class SignalService {

    public static SignalService createSignalService(SignalAccountManager accountManager, SignalConfiguration.Kind kind, String signalCliConnectionConfiguration, HttpClient httpClient) {
        return switch (kind) {
            case LOCAL -> {
                Path signalDataPath = Path.of(OpenHAB.getUserDataFolder(), "signal");
                yield new JsonRpcStdioLocalService(accountManager, signalDataPath, signalCliConnectionConfiguration);
            }
            case NETWORK ->
                    new JsonRpcTcpService(accountManager, signalCliConnectionConfiguration);
            case MANAGED -> {
                Path signalDataPath = Path.of(OpenHAB.getUserDataFolder(), "signal");
                yield new JsonRpcStdioManagedService(accountManager, signalDataPath, signalCliConnectionConfiguration, httpClient);
            }
        };
    }

    public abstract void start();
    public abstract void stop();

    public abstract DeliveryReport sendMessage(String account, String recipient, String message, @Nullable String attachment);

    public abstract boolean exists(String account) throws JsonResponseException, IOException;
    public <T> JsonResponse<T> sendRequest(@Nullable String account, String method, @Nullable Map<String, Object> parameters, Class<T> responseType) throws JsonResponseException, IOException {
        return sendRequest(account, method, parameters, responseType, 30);
    }
    public abstract <T> JsonResponse<T> sendRequest(@Nullable String account, String method, @Nullable Map<String, Object> parameters, Class<T> responseType, int waitTime) throws JsonResponseException, IOException;
}
