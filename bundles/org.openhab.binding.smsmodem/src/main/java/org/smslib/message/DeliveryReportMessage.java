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

import java.util.Date;

import org.smslib.pduUtils.gsm3040.SmsStatusReportPdu;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class DeliveryReportMessage extends InboundMessage {
    private static final long serialVersionUID = 1L;

    public enum DeliveryStatus {
        Unknown("U"),
        Pending("P"),
        Failed("F"),
        Delivered("D"),
        Expired("X"),
        Error("E");

        private final String shortString;

        private DeliveryStatus(String shortString) {
            this.shortString = shortString;
        }

        public String toShortString() {
            return this.shortString;
        }
    }

    DeliveryStatus deliveryStatus = DeliveryStatus.Unknown;

    String originalOperatorMessageId;

    Date originalReceivedDate;

    public DeliveryReportMessage() {
        super(Type.StatusReport, "", 0);
    }

    public DeliveryReportMessage(SmsStatusReportPdu pdu, String memLocation, int memIndex) {
        super(Type.StatusReport, memLocation, memIndex);
        setOriginalOperatorMessageId(String.valueOf(pdu.getMessageReference()));
        setRecipientAddress(new MsIsdn(pdu.getAddress()));
        setSentDate(pdu.getTimestamp());
        setOriginalReceivedDate(pdu.getDischargeTime());
        int i = pdu.getStatus();
        setPayload(new Payload(""));
        if ((i & 0x60) == 0) {
            setDeliveryStatus(DeliveryStatus.Delivered);
        } else if ((i & 0x20) == 0x20) {
            setDeliveryStatus(DeliveryStatus.Pending);
        } else if ((i & 0x40) == 0x40) {
            setDeliveryStatus(DeliveryStatus.Expired);
        } else if ((i & 0x60) == 0x60) {
            setDeliveryStatus(DeliveryStatus.Expired);
        } else {
            setDeliveryStatus(DeliveryStatus.Error);
        }
    }

    public DeliveryReportMessage(String messageId, String recipientAddress, String memLocation, int memIndex,
            Date originalSentDate, Date receivedDate) {
        super(Type.StatusReport, memLocation, memIndex);
        setOriginalOperatorMessageId(messageId);
        setRecipientAddress(new MsIsdn(recipientAddress));
        setSentDate(originalSentDate);
        setOriginalReceivedDate(receivedDate);
        setDeliveryStatus(DeliveryStatus.Unknown);
    }

    public DeliveryStatus getDeliveryStatus() {
        return this.deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getOriginalOperatorMessageId() {
        return this.originalOperatorMessageId;
    }

    public void setOriginalOperatorMessageId(String originalOperatorMessageId) {
        this.originalOperatorMessageId = originalOperatorMessageId;
    }

    public Date getOriginalReceivedDate() {
        return new Date(this.originalReceivedDate.getTime());
    }

    public void setOriginalReceivedDate(Date originalReceivedDate) {
        this.originalReceivedDate = new Date(originalReceivedDate.getTime());
    }

    @Override
    public String getSignature() {
        return hashSignature(String.format("%s-%s-%s-%s", getOriginatorAddress(), getOriginalOperatorMessageId(),
                getOriginalReceivedDate(), getDeliveryStatus()));
    }

    @Override
    public String toShortString() {
        return String.format("[%s @ %s = %s @ %s]", getId(), getRecipientAddress(), getDeliveryStatus(),
                getOriginalReceivedDate());
    }
}
