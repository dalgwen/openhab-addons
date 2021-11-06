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

package org.smslib.gateway.modem.driver;

import java.io.IOException;

import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.gateway.modem.Modem;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class JSerialModemDriver extends AbstractModemDriver {

    private static final int ONE_STOP_BIT = 1;
    static final public int NO_PARITY = 0;
    static final public int FLOW_CONTROL_RTS_ENABLED = 0x00000001;
    static final public int FLOW_CONTROL_CTS_ENABLED = 0x00000010;

    @SuppressWarnings("hiding")
    static Logger logger = LoggerFactory.getLogger(JSerialModemDriver.class);

    String portName;

    int baudRate;

    private final SerialPortManager serialPortManager;

    SerialPort serialPort;

    public JSerialModemDriver(SerialPortManager serialPortManager, Modem modem, String port, int baudRate) {
        super(modem);
        this.portName = port;
        this.baudRate = baudRate;
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void openPort() throws NumberFormatException, IOException {
        SerialPortIdentifier portIdentifier = serialPortManager.getIdentifier(portName);
        if (portIdentifier == null) {
            throw new IOException("SMSModem cannot use serial port " + portName);
        }
        try {
            serialPort = portIdentifier.open("org.openhab.binding.smsmodem", 2000);
            serialPort.setSerialPortParams(baudRate, 8, ONE_STOP_BIT, NO_PARITY);
            serialPort.setFlowControlMode(FLOW_CONTROL_RTS_ENABLED | FLOW_CONTROL_CTS_ENABLED);
            this.in = serialPort.getInputStream();
            this.out = serialPort.getOutputStream();
            this.pollReader = new PollReader();
            this.pollReader.start();

        } catch (PortInUseException | UnsupportedCommOperationException e) {
            throw new IOException(e);
        } catch (RuntimeException e) {
            logger.debug("Unexpected runtime exception during serial port initialized ", e);
            throw new IOException(e);
        }
    }

    @Override
    public void closePort() throws IOException, InterruptedException {
        try {
            logger.debug("Closing comm port: " + getPortInfo());
            if (this.pollReader != null) {
                this.pollReader.cancel();
                try {
                    this.pollReader.join();
                } catch (InterruptedException ex) {
                    logger.error("PollReader closing exception: {}");
                }
            }
            if (in != null) {
                this.in.close();
                this.in = null;
            }
            if (out != null) {
                this.out.close();
                this.out = null;
            }
            if (serialPort != null) {
                this.serialPort.close();
                this.serialPort = null;
            }
        } catch (Exception e) {
            logger.error("Closing port exception:\n{}", e);
        }
    }

    @Override
    public String getPortInfo() {
        return this.portName + ":" + this.baudRate;
    }
}
