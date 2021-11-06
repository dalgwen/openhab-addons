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

import java.net.URL;
import java.util.Calendar;
import java.util.Date;

import org.smslib.pduUtils.gsm3040.PduFactory;
import org.smslib.pduUtils.gsm3040.PduUtils;
import org.smslib.pduUtils.gsm3040.SmsSubmitPdu;
import org.smslib.pduUtils.wappush.WapSiPdu;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class OutboundWapMessage extends OutboundBinaryMessage {
    private static final long serialVersionUID = 1L;

    public enum WapSignals {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        DELETE
    }

    URL url;

    Date createDate, expiryDate;

    WapSignals signal;

    String siId;

    String text;

    public OutboundWapMessage(String recipientAddress, URL url, String siId, String text) {
        this(new MsIsdn(recipientAddress), url, new Date(), new Date(), WapSignals.MEDIUM, siId, text);
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR, 24);
        this.expiryDate = cal.getTime();
    }

    public OutboundWapMessage(MsIsdn recipientAddress, URL url, String siId, String text) {
        this(recipientAddress, url, new Date(), new Date(), WapSignals.MEDIUM, siId, text);
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR, 24);
        this.expiryDate = cal.getTime();
    }

    public OutboundWapMessage(MsIsdn recipientAddress, URL url, Date createDate, Date expiryDate, WapSignals wapSignal,
            String siId, String text) {
        super();
        setEncoding(Encoding.Enc8);
        setRecipientAddress(recipientAddress);
        this.url = url;
        this.createDate = new java.util.Date(createDate.getTime());
        this.expiryDate = new java.util.Date(expiryDate.getTime());
        this.signal = wapSignal;
        this.siId = siId;
        this.text = text;
        setSourcePort(9200);
        setDestinationPort(2948);
    }

    @Override
    protected WapSiPdu createPduObject(boolean extRequestDeliveryReport) {
        WapSiPdu pdu;
        if (extRequestDeliveryReport) {
            pdu = PduFactory.newWapSiPdu(PduUtils.TP_SRR_REPORT | PduUtils.TP_VPF_INTEGER);
        } else {
            pdu = PduFactory.newWapSiPdu();
        }
        return pdu;
    }

    @Override
    protected void initPduObject(SmsSubmitPdu pdu, MsIsdn smscNumber) {
        super.initPduObject(pdu, smscNumber);
        WapSiPdu wapSiPdu = (WapSiPdu) pdu;
        wapSiPdu.setIndicationText(this.text);
        wapSiPdu.setUrl(this.url.toString());
        wapSiPdu.setCreateDate(this.createDate);
        wapSiPdu.setExpireDate(this.expiryDate);
        wapSiPdu.setWapSignalFromString(this.signal.toString());
        wapSiPdu.setSiId(this.siId);
        this.payload = new Payload(wapSiPdu.getDataBytes());
    }
}
