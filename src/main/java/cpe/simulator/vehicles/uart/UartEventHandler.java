package cpe.simulator.vehicles.uart;

/** Handler d'evenement UART. */
public interface UartEventHandler {
  String eventName();

  void handle(UartMessage message);
}
