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
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.gateway.modem.Modem;
import org.smslib.helper.Common;

/**
 *
 * Extracted from SMSLib
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */

public class IPModemDriver extends AbstractModemDriver {
    @SuppressWarnings("hiding")
    static Logger logger = LoggerFactory.getLogger(IPModemDriver.class);

    String address;

    int port;

    Socket socket;

    public IPModemDriver(Modem modem, String address, int port) {
        super(modem);
        this.address = address;
        this.port = port;
    }

    @Override
    public void openPort() throws IOException, NumberFormatException {
        logger.debug("Opening IP port: " + getPortInfo());
        this.socket = new Socket(this.address, this.port);
        this.socket.setReceiveBufferSize(Integer.valueOf(getModemSettings("port_buffer")));
        this.socket.setSendBufferSize(Integer.valueOf(getModemSettings("port_buffer")));
        this.socket.setSoTimeout(30000);
        this.socket.setTcpNoDelay(true);
        this.in = this.socket.getInputStream();
        this.out = this.socket.getOutputStream();
        Common.countSheeps(Integer.valueOf(getModemSettings("after_ip_connect_wait_unit")));
        this.pollReader = new PollReader();
        this.pollReader.start();
    }

    @Override
    public void closePort() throws IOException, InterruptedException {
        logger.debug("Closing IP port: " + getPortInfo());
        this.pollReader.cancel();
        this.pollReader.join();
        this.in.close();
        this.in = null;
        this.out.close();
        this.out = null;
        this.socket.close();
        Common.countSheeps(Integer.valueOf(getModemSettings("after_ip_connect_wait_unit")));
    }

    @Override
    public String getPortInfo() {
        return this.address + ":" + this.port;
    }
}
