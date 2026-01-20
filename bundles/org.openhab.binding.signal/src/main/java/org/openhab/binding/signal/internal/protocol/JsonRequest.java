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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class JsonRequest {

    /**
     * A String specifying the version of the JSON-RPC protocol. MUST be exactly "2.0".
     */
    private final String jsonrpc;

    /**
     * A String containing the name of the method to be invoked.
     * Method names that begin with the word rpc followed by a period character (U+002E or ASCII 46)
     * are reserved for rpc-internal methods and extensions and MUST NOT be used for anything else.
     */
    private final String method;

    /**
     * A Structured value that holds the parameter values to be used during the invocation of the method.
     * This member MAY be omitted.
     */
    @Nullable
    private final Map<String, ?> params;

    /**
     * An identifier established by the Client
     */
    private final String id;


    public JsonRequest(
            @Nullable final String account,
            String method,
            @Nullable Map<String, Object> params
    ) {
        this.jsonrpc = "2.0";
        this.method = method;
        this.id = UUID.randomUUID().toString();
        if (account != null) {
            if (params == null) {
                params = new HashMap<>();
            }
            params.put("account", account);
        }
        this.params = params;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    @Nullable
    public Map<String, ?> getParams() {
        return params;
    }

    public String getId() {
        return id;
    }
}
