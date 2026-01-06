package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.UartMessageListener;
import cpe.simulator.vehicles.uart.UartEventHandler;
import cpe.simulator.vehicles.uart.UartMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Routeur de messages UART vers les handlers par evenement. */
public final class UartMessageRouter implements UartMessageListener {

  private final Map<String, UartEventHandler> handlers = new ConcurrentHashMap<>();
  private final Logger logger;

  public UartMessageRouter(Logger logger) {
    this.logger = logger;
  }

  public void register(UartEventHandler handler) {
    if (handler == null || handler.eventName() == null || handler.eventName().isBlank()) {
      throw new IllegalArgumentException("UART handler invalide");
    }
    handlers.put(handler.eventName(), handler);
  }

  @Override
  public void onMessage(UartMessage message) {
    if (message == null) {
      return;
    }
    UartEventHandler handler = handlers.get(message.event());
    if (handler == null) {
      logger.warn("Evenement UART inconnu: " + message);
      return;
    }
    logger.info("Traitement message UART: " + message);
    handler.handle(message);
  }
}
