package org.openhab.binding.voltronic.internal;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.NoSuchPortException;

@NonNullByDefault
class Serial extends Base implements Closeable {
    private @Nullable SerialPort device;
    private @Nullable ScheduledFuture<?> pollingJob;
    private final SerialPortManager serialPortManager;
    final private Logger logger = LoggerFactory.getLogger(Serial.class);
    private @Nullable InputStream input;
    private @Nullable OutputStream output;
    private String port = "";

    Serial(Thing thing, SerialPortManager serialPortManager, TranslationProvider translationProvider) {
        super(thing, translationProvider);
        this.serialPortManager = serialPortManager;
    }

    private void instantiate(final SerialPortIdentifier id)
            throws NoSuchPortException, PortInUseException, IOException {
        logger.trace("instantiate 1 {}", port);
        device = id.open(Serial.class.getSimpleName(), 1000); // calls nativeavailable
        logger.trace("instantiate 2 {}", port);
        if (device == null)
            logger.trace("IS device null? " + (device == null ? "yes" : "no"));
        try {
            input = device.getInputStream();
            output = device.getOutputStream();
        } catch (IOException e) {
            logger.error("CLOSING in Serial ctor because getInputStream/getOutputStream failed");
            device.close();
            device = null;
            logger.error("Serial: close socket from ctor");
            throw e;
        }
        logger.trace("instantiate 3 {}", port);
        try {
            /*
             * read(1) on hidraw devices does not work, so read(byte[]) must be done. But
             * ioctl(…FIORDCHK, 0) also does not work, so to avoid calling RXTXPort.nativeavailable
             * threshold must be set. It turns out that the received data arrives in batches of 8.
             */
            device.enableReceiveThreshold(8);
        } catch (Exception e) {
            /*
             * IOException: Invalid argument in TimeoutThreshold, likewise for below. The reason
             * is that RXTXPort.NativeEnableReceiveTimeoutThreshold aborts when tcgetattr() fails.
             * Why Excepiton and not IOException? Because the declaration in RXTXPort.java for
             * native void NativeEnableReceiveTimeoutThreshold does not say it can throw an
             * exception, so it throws only RuntimeExceptions in theory and IOException in practice.
             */
        }
        logger.trace("instantiate 4 {}", port);
        try { // useful when the cable is unplugged or device is off
            device.enableReceiveTimeout(1300);
        } catch (Exception e) {
        } // as above
        logger.trace("instantiate 5 END {}", port);
    }

    @Override
    public void run() {
        final SerialPortIdentifier portId = serialPortManager.getIdentifier(port);
        if (portId == null) {
            goOffline();
            return;
        }
        if (logger.isDebugEnabled()) {
            StringBuilder stackTrace = new StringBuilder();
            for (var s : Thread.currentThread().getStackTrace())
                stackTrace.append(s.toString() + "\n");
            logger.debug("goOnline {} stackTrace {}", port, stackTrace.toString());
        }
        isOnline = true;
        try {
            instantiate(portId);
        } catch (NoSuchPortException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "No such port " + port);
            logger.error("No such port, going offline", port);
            goOffline();
            return;
        } catch (PortInUseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Port " + port + " is in use");
            logger.error("Port {} is in use, going offline", port);
            goOffline();
            return;
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "IOException, see logs.");
            logger.error("IOException going offline on port {}", port, e);
            goOffline();
            return;
        }
        setProperties();
    }

    @Override
    public synchronized void dispose() {
        final var job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        close();
        unsetChannelsAndProperties();
    }

    @Override
    public void initialize() {
        final Configuration config = getConfig();
        port = (String) config.get("port");
        int refreshInterval = config.containsKey("refreshInterval")
                ? ((Number) config.get("refreshInterval")).intValue()
                : 750;
        pollingJob = scheduler.scheduleWithFixedDelay(this::pollingCode, 0, refreshInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    protected synchronized void pollingCode() {
        if (device == null) {
            try {
                run();
            } catch (TimeoutException e) {
            }
            return;
        }
        super.pollingCode();
    }

    @Override
    protected void goOffline() {
        if (isOnline && logger.isDebugEnabled()) {
            StringBuilder stackTrace = new StringBuilder();
            for (var s : Thread.currentThread().getStackTrace())
                stackTrace.append(s.toString() + "\n");
            logger.debug("goOffline {} stackTrace {}", port, stackTrace.toString());
        }
        super.goOffline();
        close();
    }

    /*
     * Returns null on timeout, empty string on error, empty string if the answer does not end in \r
     * Sending QPIWIS returns null, QPR returns NAK
     */
    @Override
    @Nullable
    public String command(final byte[] command) {
        synchronized (device) {
            try {
                output.write(command);
                /*
                 * This could be useful, when connecting to Voltronic over LAN cable, RS232 or Bluetooth
                 * try { // on hidraw devices ioctl(8, TCSBRK, 1) causes "Invalid argument in nativeDrain"
                 * output.flush();
                 * } catch (IOException e) { }
                 */
            } catch (IOException e) {
                switch (e.getMessage()) {
                    case "Broken pipe in writeArray":
                        logger.error("{} - Broken pipe {}", new String(command, 0, command.length - 3), port);
                        return null; // concurrent access to port
                    case "Connection timed out in writeArray":
                        logger.error("{} on sending invalid command", new String(command, 0, command.length - 3));
                        return null; // on sending QPIWIS
                    case "No such device in writeArray":
                        logger.error("{} unplugged cable (write)", new String(command, 0, command.length - 3));
                        return null; // on unplugged cable
                    default:
                        logger.error("{} produces exception: ", new String(command, 0, command.length - 3), e);
                        return null;
                }
            }
            var sb = new StringBuilder();
            byte[] b = new byte[8];

            while (true)
                try {
                    switch (input.read(b, 0, b.length)) {
                        case -1:
                            logger.error("B Serial.command:input read {} returned -1: returning empty string for {}",
                                    new String(command, 0, command.length - 3), port);
                            return "";
                        case 0:
                            logger.trace("A Serial.command:input read {} returned zero (timeout) for {}",
                                    new String(command, 0, command.length - 3), port);
                            return null;
                        default:
                            for (int by : b)
                                if (by == 13)
                                    return sb.toString();
                                else
                                    sb.append((char) by);
                    }
                } catch (IOException e) {
                    switch (e.getMessage()) {
                        case "Bad file descriptor in readArray":
                            logger.error("IOException in read (bad file descriptor)", e);
                            return null;
                        case "Success in readArray":
                            logger.error("IOException in read (Success in readArray", e);
                            return null;
                        case "Invalid argument in readArray":
                            logger.error("IOException in read (Invalid argument in readArray", e);
                            return null;
                        case "Input/output error in readArray":
                            logger.error("IOException in read (Input/output error in readArray)", e);
                            return null;
                        default:
                            // whatever happens, log the exception and return null
                            logger.error("Exception from RXTX.read()", e);
                            return null;
                    }
                }
        }
    }

    @Override
    public void close() {
        final var localR = device;
        if (localR != null) {
            if (logger.isDebugEnabled()) {
                StringBuilder stackTrace = new StringBuilder();
                for (var s : Thread.currentThread().getStackTrace())
                    stackTrace.append(s.toString() + "\n");
                logger.debug("CLOSING PORT from {}", stackTrace.toString());
            }
            localR.close();
            device = null;
        } else if (logger.isDebugEnabled()) {
            StringBuilder stackTrace = new StringBuilder();
            for (var s : Thread.currentThread().getStackTrace())
                stackTrace.append(s.toString() + "\n");
            logger.debug("PORT already CLOSED from {}", stackTrace.toString());
        }
    }
}
