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
package org.openhab.binding.smsmodem.internal.smslib;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.gateway.AbstractGateway.Status;
import org.smslib.gateway.modem.Modem;
import org.smslib.message.InboundMessage;
import org.smslib.message.OutboundMessage;

/**
 * Handle the communication with the SMSlib service
 * (abstract a gateway and use smslib service instance transparently)
 *
 * @author Gwendal ROULLEAU - Initial contribution
 *
 */
@NonNullByDefault
public class SMSModem {

    private final Logger logger = LoggerFactory.getLogger(SMSModem.class);

    private String serialPortOrIP;

    private final Integer baudOrNetworkPort;

    private final MessageReceiver messageReceiver;

    private final ServiceStatusListener statusListener;

    private SerialPortManager serialPortManager;

    protected @Nullable Modem gateway;

    private String id;

    private @Nullable String simPin;

    public SMSModem(SerialPortManager serialPortManager, String serialPortOrIP, Integer baudOrNetworkPort,
            @Nullable String simPin, MessageReceiver messageReceiver, ServiceStatusListener statusListener) {
        super();
        this.serialPortOrIP = serialPortOrIP;
        this.baudOrNetworkPort = baudOrNetworkPort;
        this.messageReceiver = messageReceiver;
        this.statusListener = statusListener;
        this.id = serialPortOrIP + "-" + baudOrNetworkPort;
        this.serialPortManager = serialPortManager;
        this.simPin = (simPin == null || simPin.isEmpty()) ? null : simPin;
    }

    public String getId() {
        return id;
    }

    public synchronized void start() throws ModemConfigurationException {

        if (this.gateway != null) {// ensure the underlying modem is stopped before trying to (re)starting it
            SMSLibService.instance.unregisterModem(this);
        }

        checkParam();
        this.gateway = new Modem(serialPortManager, id, serialPortOrIP, Integer.valueOf(baudOrNetworkPort), simPin);
        logger.debug("Trying to start SMSModem {}/{}", serialPortOrIP, baudOrNetworkPort);

        SMSLibService.instance.registerModem(this);
        logger.debug("SMSModem {}/{} started", serialPortOrIP, baudOrNetworkPort);
    }

    private String resolveEventualSymbolicLink(String serialPortOrIp) {
        String keepResult = serialPortOrIp;
        Path maybePath = Paths.get(serialPortOrIp);
        File maybeFile = maybePath.toFile();
        if (maybeFile.exists() && Files.isSymbolicLink(maybePath)) {
            try {
                maybePath = maybePath.toRealPath();
                keepResult = maybePath.toAbsolutePath().toString();
            } catch (IOException e) {
            } // nothing to do, not a valid symbolic link, return
        }
        return keepResult;
    }

    private void checkParam() throws ModemConfigurationException {

        this.serialPortOrIP = resolveEventualSymbolicLink(this.serialPortOrIP);
        SerialPortIdentifier identifier = serialPortManager.getIdentifier(this.serialPortOrIP);
        if (identifier == null) {
            try {
                InetAddress inetAddress = InetAddress.getByName(this.serialPortOrIP);
                this.serialPortOrIP = inetAddress.getHostAddress();

                Socket s = null; // test reachable address
                try {
                    s = new Socket(serialPortOrIP, this.baudOrNetworkPort);
                } finally {
                    if (s != null) {
                        try {
                            s.close();
                        } catch (Exception e) {
                        }
                    }
                }

            } catch (IOException | NumberFormatException ex) {
                // no serial port and no ip
                throw new ModemConfigurationException(this.serialPortOrIP + " with " + baudOrNetworkPort
                        + " is not a serial port/baud or a reachable address/port");
            }
        }
    }

    public synchronized void stop() {
        SMSLibService.instance.unregisterModem(this);
        Modem gatewayFinal = gateway;
        if (gatewayFinal != null) {
            gatewayFinal.stop();
        }
    }

    public synchronized void send(String dest, String message) throws ModemCommunicationException {
        OutboundMessage out = new OutboundMessage(dest, message);
        logger.debug("Sending message to {}", dest);
        try {
            Modem gatewayFinal = gateway;
            if (gatewayFinal != null) {
                gatewayFinal.send(out);
            }
        } catch (Exception e) {
            throw new ModemCommunicationException(e);
        }
    }

    public synchronized boolean receive(InboundMessage message) {
        logger.debug("Receiving SMS from {}", message.getOriginatorAddress().getAddress());
        String sender = message.getOriginatorAddress().getAddress();
        String messageText = message.getPayload().getText();
        try {
            Modem gatewayFinal = gateway;
            if (gatewayFinal != null) {
                gatewayFinal.delete(message);
            }
        } catch (Exception e) {
            logger.error("Cannot delete message after delivering it", e);
        }
        messageReceiver.receive(sender, messageText);
        return true;
    }

    public boolean newStatus(Status newStatus) {

        switch (newStatus) {
            case Error:
                logger.debug("SMSModem {}/{} is on error", this.serialPortOrIP, this.baudOrNetworkPort);
                statusListener.error();
                break;
            case Started:
                logger.debug("SMSModem {}/{} is started", this.serialPortOrIP, this.baudOrNetworkPort);
                statusListener.started();
                break;
            case Starting:
                logger.debug("SMSModem {}/{} is starting", this.serialPortOrIP, this.baudOrNetworkPort);
                statusListener.starting();
                break;
            case Stopped:
                logger.debug("SMSModem {}/{} is stopped", this.serialPortOrIP, this.baudOrNetworkPort);
                statusListener.stopped();
                break;
            case Stopping:
                logger.debug("SMSModem {}/{} is stopping", this.serialPortOrIP, this.baudOrNetworkPort);
                statusListener.stopping();
                break;
        }
        return false;
    }
}
