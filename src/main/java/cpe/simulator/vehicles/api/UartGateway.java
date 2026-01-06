package cpe.simulator.vehicles.api;

import cpe.simulator.vehicles.uart.UartMessage;

/** Abstraction du transport UART. */
public interface UartGateway extends AutoCloseable {
  void start(UartMessageListener listener);

  void send(UartMessage message);

  @Override
  void close();
}
