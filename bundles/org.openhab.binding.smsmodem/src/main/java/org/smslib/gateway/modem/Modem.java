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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.openhab.core.io.transport.serial.SerialPortManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.core.Capabilities;
import org.smslib.core.Capabilities.Caps;
import org.smslib.core.Coverage;
import org.smslib.core.CreditBalance;
import org.smslib.core.Settings;
import org.smslib.gateway.AbstractGateway;
import org.smslib.gateway.modem.DeviceInformation.Modes;
import org.smslib.gateway.modem.driver.AbstractModemDriver;
import org.smslib.gateway.modem.driver.IPModemDriver;
import org.smslib.gateway.modem.driver.JSerialModemDriver;
import org.smslib.message.DeliveryReportMessage.DeliveryStatus;
import org.smslib.message.InboundMessage;
import org.smslib.message.MsIsdn;
import org.smslib.message.MsIsdn.Type;
import org.smslib.message.OutboundMessage;
import org.smslib.message.OutboundMessage.FailureCause;
import org.smslib.message.OutboundMessage.SentStatus;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class Modem extends AbstractGateway {
    static Logger logger = LoggerFactory.getLogger(Modem.class);

    DeviceInformation deviceInformation = new DeviceInformation();

    AbstractModemDriver modemDriver;

    String simPin;

    String simPin2 = null;

    MsIsdn smscNumber;

    MessageReader messageReader;

    ModemScheduler modemScheduler;

    HashSet<String> readMessagesSet;

    public Modem(SerialPortManager serialPortManager, String gatewayId, String address, int port, String simPin) {
        super(2, gatewayId, "GSM Modem");
        Capabilities caps = new Capabilities();
        caps.set(Caps.CanSendMessage);
        caps.set(Caps.CanSendBinaryMessage);
        caps.set(Caps.CanSendUnicodeMessage);
        caps.set(Caps.CanSendWapMessage);
        caps.set(Caps.CanSendFlashMessage);
        caps.set(Caps.CanSendPortInfo);
        caps.set(Caps.CanSplitMessages);
        caps.set(Caps.CanRequestDeliveryStatus);
        setCapabilities(caps);
        if (isPortAnIpAddress(address)) {
            this.modemDriver = new IPModemDriver(this, address, port);
        } else {
            this.modemDriver = new JSerialModemDriver(serialPortManager, this, address, port);
        }
        this.simPin = simPin;
        this.smscNumber = (smscNumber == null ? new MsIsdn() : smscNumber);
        this.readMessagesSet = new HashSet<>();
    }

    public DeviceInformation getDeviceInformation() {
        return this.deviceInformation;
    }

    @Override
    public void _start() throws Exception {
        this.modemDriver.lock.lock();
        try {
            this.modemDriver.openPort();
            this.modemDriver.initializeModem();
            this.messageReader = new MessageReader(this);
            this.messageReader.start();
            this.modemScheduler = new ModemScheduler(this);
            this.modemScheduler.addTask(new ModemSchedulerTask(this, Settings.modemRssiPollingInterval) {
                @Override
                protected void execute() throws Exception {
                    this.modem.refreshRssi();
                }
            });
            this.modemScheduler.start();
            logger.debug(String.format("Gateway: %s: %s, SL:%s, SIG: %s / %s", toShortString(), getDeviceInformation(),
                    this.modemDriver.getMemoryLocations(), this.modemDriver.getSignature(true),
                    this.modemDriver.getSignature(false)));
        } finally {
            this.modemDriver.lock.unlock();
        }
    }

    @Override
    public void _stop() throws Exception {
        this.modemDriver.lock.lock();
        try {
            if (this.messageReader != null) {
                this.messageReader.cancel();
                this.messageReader.join();
                this.messageReader = null;
            }
            if (this.modemScheduler != null) {
                this.modemScheduler.cancel();
                this.modemScheduler.join();
                this.modemScheduler = null;
            }
            this.modemDriver.closePort();
        } catch (Exception e) {

        } finally {
            this.modemDriver.lock.unlock();
        }
    }

    public AbstractModemDriver getModemDriver() {
        return this.modemDriver;
    }

    public String getSimPin() {
        return this.simPin;
    }

    public String getSimPin2() {
        return this.simPin2;
    }

    public MsIsdn getSmscNumber() {
        return this.smscNumber;
    }

    public void setSmscNumber(MsIsdn smscNumber) {
        this.smscNumber = smscNumber;
    }

    public HashSet<String> getReadMessagesSet() {
        return this.readMessagesSet;
    }

    public void refreshDeviceInfo() throws Exception {
        this.modemDriver.lock.lock();
        try {
            this.modemDriver.refreshDeviceInformation();
        } finally {
            this.modemDriver.lock.unlock();
        }
    }

    public void refreshRssi() throws Exception {
        this.modemDriver.lock.lock();
        try {
            this.modemDriver.refreshRssi();
        } finally {
            this.modemDriver.lock.unlock();
        }
    }

    public boolean setEncoding(String encoding) throws Exception {
        return (this.modemDriver.atSetEncoding(encoding).isResponseOk());
    }

    public ModemResponse sendATCommand(String atCommand) throws Exception {
        return this.modemDriver.write(atCommand);
    }

    @Override
    public boolean _send(OutboundMessage message) throws Exception {
        this.modemDriver.lock.lock();
        try {
            if (getDeviceInformation().getMode() == Modes.PDU) {
                List<String> pdus = message.getPdus(getSmscNumber(), getNextMultipartReferenceNo(),
                        getRequestDeliveryReport());
                for (String pdu : pdus) {
                    int j = pdu.length() / 2;
                    if (getSmscNumber() == null) {
                        // Do nothing on purpose!
                    } else if (getSmscNumber().getType() == Type.Void) {
                        j--;
                    } else {
                        int smscNumberLen = getSmscNumber().getAddress().length();
                        if (smscNumberLen % 2 != 0) {
                            smscNumberLen++;
                        }
                        int smscLen = (2 + smscNumberLen) / 2;
                        j = j - smscLen - 1;
                    }
                    int refNo = this.modemDriver.atSendPDUMessage(j, pdu);
                    if (refNo >= 0) {
                        message.setGatewayId(getGatewayId());
                        message.setSentDate(new Date());
                        message.getOperatorMessageIds().add(String.valueOf(refNo));
                        message.setSentStatus(SentStatus.Sent);
                        message.setFailureCause(FailureCause.None);
                    } else {
                        message.setSentStatus(SentStatus.Failed);
                        message.setFailureCause(FailureCause.GatewayFailure);
                    }
                }
            } else {
                int refNo = this.modemDriver.atSendTEXTMessage(message.getRecipientAddress().getAddress(),
                        message.getPayload().getText());
                if (refNo >= 0) {
                    message.setGatewayId(getGatewayId());
                    message.setSentDate(new Date());
                    message.getOperatorMessageIds().add(String.valueOf(refNo));
                    message.setSentStatus(SentStatus.Sent);
                    message.setFailureCause(FailureCause.None);
                } else {
                    message.setSentStatus(SentStatus.Failed);
                    message.setFailureCause(FailureCause.GatewayFailure);
                }
            }
            return (message.getSentStatus() == SentStatus.Sent);
        } finally {
            this.modemDriver.lock.unlock();
        }
    }

    @Override
    protected boolean _delete(InboundMessage message) throws Exception {
        this.modemDriver.lock.lock();
        try {
            this.readMessagesSet.remove(message.getSignature());
            if (message.getMemIndex() >= 0) {
                return this.modemDriver.atDeleteMessage(message.getMemLocation(), message.getMemIndex()).isResponseOk();
            }
            if ((message.getMemIndex() == -1) && (message.getMpMemIndex().length() > 0)) {
                StringTokenizer tokens = new StringTokenizer(message.getMpMemIndex(), ",");
                while (tokens.hasMoreTokens()) {
                    this.modemDriver.atDeleteMessage(message.getMemLocation(), Integer.valueOf(tokens.nextToken()));
                }
                return true;
            }
            return false;
        } finally {
            this.modemDriver.lock.unlock();
        }
    }

    @Override
    protected DeliveryStatus _queryDeliveryStatus(String operatorMessageId) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected CreditBalance _queryCreditBalance() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Coverage _queryCoverage(Coverage coverage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toShortString() {
        return super.toShortString() + String.format(" [%s]", this.modemDriver.getPortInfo());
    }

    private boolean isPortAnIpAddress(String address) {
        try {
            InetAddress.getByName(address);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
