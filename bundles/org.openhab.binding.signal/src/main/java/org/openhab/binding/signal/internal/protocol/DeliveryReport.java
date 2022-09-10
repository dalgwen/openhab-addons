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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 *
 * DeliveryReport DTO for reporting status delivery
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class DeliveryReport {

    public DeliveryStatus deliveryStatus;

    @Nullable
    public String aci;

    @Nullable
    public String e164;

    public DeliveryReport(DeliveryStatus deliveryStatus, @Nullable String aci, @Nullable String e164) {
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

    public void setAci(String aci) {
        this.aci = aci;
    }

    @Nullable
    public String getE164() {
        return e164;
    }

    public void setE164(String e164) {
        this.e164 = e164;
    }
}
