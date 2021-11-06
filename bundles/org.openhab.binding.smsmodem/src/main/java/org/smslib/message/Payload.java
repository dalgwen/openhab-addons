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

package org.smslib.message;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class Payload {
    public enum Type {
        Text,
        Binary
    }

    private String textData;

    private byte[] binaryData;

    private Type type;

    public Payload(String data) {
        this.type = Type.Text;
        this.textData = data;
    }

    public Payload(byte[] data) {
        this.type = Type.Binary;
        this.binaryData = data.clone();
    }

    public Payload(Payload p) {
        this.type = p.getType();
        this.textData = (this.type == Type.Text ? p.getText() : "");
        this.binaryData = (this.type == Type.Binary ? p.getBytes().clone() : null);
    }

    public Type getType() {
        return this.type;
    }

    public String getText() {
        return (this.type == Type.Text ? this.textData : null);
    }

    public byte[] getBytes() {
        return (this.type == Type.Binary ? this.binaryData : null);
    }

    public boolean isMultipart() {
        return false;
    }
}
