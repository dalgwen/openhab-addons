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

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jdt.annotation.Nullable;

/**
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
public class JsonResponse<T> {

    /**
     * A String specifying the version of the JSON-RPC protocol. MUST be exactly "2.0".
     */
    String jsonrpc = "2.0";

    /**
     * This member is REQUIRED on success.
     * This member MUST NOT exist if there was an error invoking the method.
     * The value of this member is determined by the method invoked on the Server.
     */
    @Nullable
    T result;

    /**
     * This member is REQUIRED on error.
     * This member MUST NOT exist if there was no error triggered during invocation.
     */
    Error error;

    /**
     * This member is REQUIRED.
     * It MUST be the same as the value of the id member in the Request Object.
     * If there was an error in detecting the id in the Request object (e.g. Parse error/Invalid Request), it MUST be Null.
     */
    String id;

    private JsonResponse() {
    }

    public static <T> JsonResponse<T> none(Class<T> _clazz) {
        return new JsonResponse<>();
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    @Nullable
    public T getResult() {
        return result;
    }

    public Error getError() {
        return error;
    }

    public String getId() {
        return id;
    }

    public static class Error {

        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;

        /**
         * A Number that indicates the error type that occurred.
         * This MUST be an integer.
         */
        int code;

        /**
         * A String providing a short description of the error.
         * The message SHOULD be limited to a concise single sentence.
         */
        String message;

        /**
         * A Primitive or Structured value that contains additional information about the error.
         * This may be omitted.
         * The value of this member is defined by the Server (e.g. detailed error information, nested errors etc.).
         */
        JsonNode data;

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public JsonNode getData() {
            return data;
        }
    }
}
