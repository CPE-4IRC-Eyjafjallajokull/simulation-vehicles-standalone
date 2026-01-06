package cpe.simulator.vehicles.infrastructure.uart;

import com.fazecast.jSerialComm.SerialPort;
import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.UartGateway;
import cpe.simulator.vehicles.api.UartMessageListener;
import cpe.simulator.vehicles.uart.UartMessage;
import cpe.simulator.vehicles.uart.UartMessageParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Implementation UART basee sur jSerialComm. */
public final class SerialUartGateway implements UartGateway {

  private final String portName;
  private final int baudRate;
  private final long reconnectMs;
  private final int readTimeoutMs;
  private final long writeDelayMs;
  private final UartMessageParser parser;
  private final Logger logger;

  private final Object writeLock = new Object();
  private volatile boolean running;
  private Thread readerThread;
  private SerialPort port;
  private InputStream input;
  private OutputStream output;

  public SerialUartGateway(
      String portName,
      int baudRate,
      long reconnectMs,
      int readTimeoutMs,
      long writeDelayMs,
      UartMessageParser parser,
      Logger logger) {
    this.portName = portName;
    this.baudRate = baudRate;
    this.reconnectMs = reconnectMs;
    this.readTimeoutMs = readTimeoutMs;
    this.writeDelayMs = writeDelayMs;
    this.parser = parser;
    this.logger = logger;
  }

  @Override
  public void start(UartMessageListener listener) {
    if (running) {
      return;
    }
    running = true;
    readerThread = new Thread(() -> readLoop(listener), "uart-reader");
    readerThread.setDaemon(true);
    readerThread.start();
  }

  @Override
  public void send(UartMessage message) {
    String line = parser.serialize(message);
    byte[] payload = (line + "\n").getBytes(StandardCharsets.US_ASCII);
    synchronized (writeLock) {
      if (output == null) {
        return;
      }
      try {
        output.write(payload);
        output.flush();
        sleep(writeDelayMs);
      } catch (IOException e) {
        logger.warn("Erreur ecriture UART: " + e.getMessage());
      }
    }
  }

  @Override
  public void close() {
    running = false;
    closePort();
  }

  private void readLoop(UartMessageListener listener) {
    while (running) {
      if (!openPort()) {
        sleep(reconnectMs);
        continue;
      }

      byte[] buffer = new byte[256];
      StringBuilder lineBuffer = new StringBuilder();
      while (running) {
        try {
          int read = input.read(buffer);
          if (read < 0) {
            throw new IOException("UART closed");
          }
          if (read == 0) {
            continue;
          }
          for (int i = 0; i < read; i++) {
            char ch = (char) buffer[i];
            if (ch == '\r') {
              continue;
            }
            if (ch == '\n') {
              String line = lineBuffer.toString().trim();
              lineBuffer.setLength(0);
              if (line.isEmpty()) {
                continue;
              }
              try {
                UartMessage message = parser.parse(line);
                listener.onMessage(message);
              } catch (IllegalArgumentException e) {
                // logger.warn("Message UART invalide: " + e.getMessage());
                continue;
              }
            } else {
              lineBuffer.append(ch);
            }
          }
        } catch (IOException e) {
          if (isTimeout(e)) {
            continue;
          }
          logger.warn("Erreur lecture UART: " + e.getMessage());
          break;
        }
      }

      closePort();
      sleep(reconnectMs);
    }
  }

  private boolean openPort() {
    if (port != null && port.isOpen()) {
      return true;
    }

    try {
      SerialPort candidate = SerialPort.getCommPort(portName);
      if (candidate == null) {
        logger.warn("NO UART CONNECTION");
        return false;
      }
      candidate.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
      candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, readTimeoutMs, 0);

      if (!candidate.openPort()) {
        logger.warn("NO UART CONNECTION");
        return false;
      }

      port = candidate;
      input = port.getInputStream();
      synchronized (writeLock) {
        output = port.getOutputStream();
      }

      logger.info("UART connectee sur " + portName + " @ " + baudRate + " baud");
      return true;
    } catch (Exception e) {
      logger.warn("NO UART CONNECTION");
      return false;
    }
  }

  private void closePort() {
    synchronized (writeLock) {
      if (output != null) {
        try {
          output.close();
        } catch (IOException ignored) {
        }
        output = null;
      }
      if (input != null) {
        try {
          input.close();
        } catch (IOException ignored) {
        }
        input = null;
      }
      if (port != null) {
        port.closePort();
        port = null;
      }
    }
  }

  private boolean isTimeout(IOException e) {
    String message = e.getMessage();
    if (message == null) {
      return false;
    }
    return message.toLowerCase().contains("timed out");
  }

  private void sleep(long delayMs) {
    if (delayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
