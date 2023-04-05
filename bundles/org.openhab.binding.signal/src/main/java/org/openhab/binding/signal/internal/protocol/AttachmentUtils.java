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
package org.openhab.binding.signal.internal.protocol;

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FutureResponseListener;
import org.eclipse.jetty.http.HttpMethod;

/**
 *
 * Utility class for creating attachements in dataURI format
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
public class AttachmentUtils {

    private static String createDataUriFromData(byte[] data, String mimeType) {
        StringBuffer result = new StringBuffer();
        result.append("data:").append(mimeType).append(";filename=image;base64,")
                .append(Base64.getEncoder().encodeToString(data));
        return result.toString();
    }

    public static String createAttachmentFromHttp(HttpClient httpClient, String httpUrl)
            throws AttachmentCreationException {
        HttpClient client = httpClient;
        if (client == null) {
            throw new AttachmentCreationException("Cannot get http client !");
        }
        Request request = client.newRequest(httpUrl).method(HttpMethod.GET).idleTimeout(5, TimeUnit.SECONDS);
        try {
            FutureResponseListener listener = new FutureResponseListener(request, 10 * 1024 * 1024);
            request.send(listener);
            ContentResponse contentResponse = listener.get();
            if (contentResponse.getStatus() == 200) {
                byte[] fileContent = contentResponse.getContent();
                String mimeType = contentResponse.getMediaType();
                return createDataUriFromData(fileContent, mimeType);
            } else {
                String message = "Download from " + httpUrl + " failed with status: " + contentResponse.getStatus();
                throw new AttachmentCreationException(message);
            }
        } catch (InterruptedException | ExecutionException e) {
            String message = "Download from " + httpUrl + " failed with exception: " + e.getMessage();
            throw new AttachmentCreationException(message, e);
        }
    }
}
