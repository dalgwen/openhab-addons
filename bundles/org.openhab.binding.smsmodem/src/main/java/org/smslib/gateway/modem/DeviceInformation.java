/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

package org.smslib.gateway.modem;

import java.util.Collection;
import java.util.HashSet;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class DeviceInformation {

    public enum Modes {
        PDU,
        TEXT
    }

    String manufacturer = "N/A";

    String model = "N/A";

    String swVersion = "N/A";

    String serialNo = "N/A";

    String imsi = "N/A";

    int rssi = 0;

    Modes mode;

    Collection<String> supportedEncodings = new HashSet<>();

    String encoding;

    public String getManufacturer() {
        return this.manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSwVersion() {
        return this.swVersion;
    }

    public void setSwVersion(String swVersion) {
        this.swVersion = swVersion;
    }

    public String getSerialNo() {
        return this.serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getImsi() {
        return this.imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public int getRssi() {
        return this.rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public Modes getMode() {
        return this.mode;
    }

    public void setMode(Modes mode) {
        this.mode = mode;
    }

    public Collection<String> getSupportedEncodings() {
        return this.supportedEncodings;
    }

    public String getEncoding() {
        return this.encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    public String toString() {
        return String.format("MANUF:%s, MODEL:%s, SERNO:%s, IMSI:%s, SW:%s, RSSI:%ddBm, MODE:%s", getManufacturer(),
                getModel(), getSerialNo(), getImsi(), getSwVersion(), getRssi(), getMode());
    }
}
