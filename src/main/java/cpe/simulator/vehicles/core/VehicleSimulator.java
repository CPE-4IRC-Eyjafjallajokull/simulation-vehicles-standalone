package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.UartGateway;
import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.uart.UartMessage;
import cpe.simulator.vehicles.uart.UartMessageParser;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Boucle principale de simulation des vehicules. */
public final class VehicleSimulator {

  private final Fleet fleet;
  private final MovementModel movementModel;
  private final UartGateway uartGateway;
  private final UartMessageRouter router;
  private final Logger logger;
  private final Clock clock;
  private final long tickMs;
  private final long baseSendIntervalMs;
  private final long assignmentSendIntervalMs;
  private final String positionEvent;
  private final boolean logSends;
  private final UartMessageParser uartParser;
  private final Map<String, Long> lastSendMs = new HashMap<>();
  private final Map<String, Boolean> lastAtAssignment = new HashMap<>();

  public VehicleSimulator(
      Fleet fleet,
      MovementModel movementModel,
      UartGateway uartGateway,
      UartMessageRouter router,
      Logger logger,
      Clock clock,
      long tickMs,
      long baseSendIntervalMs,
      long assignmentSendIntervalMs,
      String positionEvent,
      boolean logSends) {
    this.fleet = fleet;
    this.movementModel = movementModel;
    this.uartGateway = uartGateway;
    this.router = router;
    this.logger = logger;
    this.clock = clock;
    this.tickMs = tickMs;
    this.baseSendIntervalMs = baseSendIntervalMs;
    this.assignmentSendIntervalMs = assignmentSendIntervalMs;
    this.positionEvent = positionEvent;
    this.logSends = logSends;
    this.uartParser = new UartMessageParser();
  }

  public void run() {
    logger.info("Vehicules charges: " + fleet.size());
    uartGateway.start(router);

    double tickSeconds = tickMs / 1_000.0;

    try {
      while (!Thread.currentThread().isInterrupted()) {
        long tickStart = System.nanoTime();
        long nowMs = clock.millis();
        long timestampSeconds = nowMs / 1_000L;

        for (VehicleSnapshot snapshot : fleet.advanceAll(movementModel, tickSeconds)) {
          long intervalMs = sendIntervalMs(snapshot);
          Long last = lastSendMs.get(snapshot.immatriculation());
          if (last == null || nowMs - last >= intervalMs) {
            GeoPoint nextPosition = snapshot.position();
            UartMessage message =
                UartMessage.position(
                    positionEvent, snapshot.immatriculation(), nextPosition, timestampSeconds);
            uartGateway.send(message);
            lastSendMs.put(snapshot.immatriculation(), nowMs);
            if (logSends) {
              logger.info("UART >> " + uartParser.serialize(message));
            }
          }
          logArrival(snapshot);
        }

        long elapsedMs = (System.nanoTime() - tickStart) / 1_000_000L;
        long sleepMs = tickMs - elapsedMs;
        if (sleepMs > 0) {
          Thread.sleep(sleepMs);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn("Simulation interrompue");
    } finally {
      uartGateway.close();
    }
  }

  private long sendIntervalMs(VehicleSnapshot snapshot) {
    GeoPoint base = snapshot.base();
    if (base != null && movementModel.isAtTarget(snapshot.position(), base)) {
      return baseSendIntervalMs;
    }
    if (snapshot.assignmentTarget() != null) {
      return assignmentSendIntervalMs;
    }
    return baseSendIntervalMs;
  }

  private void logArrival(VehicleSnapshot snapshot) {
    GeoPoint target = snapshot.assignmentTarget();
    if (target == null) {
      lastAtAssignment.put(snapshot.immatriculation(), false);
      return;
    }
    boolean atTarget = movementModel.isAtTarget(snapshot.position(), target);
    Boolean wasAtTarget = lastAtAssignment.put(snapshot.immatriculation(), atTarget);
    if (Boolean.FALSE.equals(wasAtTarget) || wasAtTarget == null) {
      if (atTarget) {
        logger.info("Vehicule arrive a destination: " + snapshot.immatriculation());
        logger.info("Retour à la base.");
        fleet.assignToBase(snapshot.immatriculation());
      }
    }
  }

  
}
