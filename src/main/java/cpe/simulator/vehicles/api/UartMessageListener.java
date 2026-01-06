package cpe.simulator.vehicles.api;

import cpe.simulator.vehicles.uart.UartMessage;

/** Callback de reception des messages UART parsees. */
public interface UartMessageListener {
  void onMessage(UartMessage message);
}
