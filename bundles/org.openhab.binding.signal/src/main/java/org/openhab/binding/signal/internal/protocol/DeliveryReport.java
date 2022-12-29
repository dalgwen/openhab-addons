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

import org.asamk.signal.manager.api.RecipientAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.whispersystems.signalservice.api.util.UuidUtil;

/**
 *
 * DeliveryReport DTO for reporting status delivery
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class DeliveryReport {

    public final DeliveryStatus deliveryStatus;

    @Nullable
    private final String aci;
    @Nullable
    private final String e164;

    public DeliveryReport(DeliveryStatus deliveryStatus, RecipientAddress recipientAddress) {
        super();
        this.deliveryStatus = deliveryStatus;
        this.aci = recipientAddress.uuid().map(uuid -> uuid.toString()).orElse(null);
        this.e164 = recipientAddress.number().orElse(null);
    }

    public DeliveryReport(DeliveryStatus deliveryStatus, String id) {
        super();
        this.deliveryStatus = deliveryStatus;
        if (UuidUtil.isUuid(id)) {
            this.aci = id;
            this.e164 = null;
        } else {
            this.e164 = id;
            this.aci = null;
        }
    }

    public DeliveryReport(DeliveryStatus deliveryStatus, String e164, String aci) {
        super();
        this.deliveryStatus = deliveryStatus;
        this.aci = aci;
        this.e164 = e164;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    @Nullable
    public String getAci() {
        return aci;
    }

    @Nullable
    public String getE164() {
        return e164;
    }
}
