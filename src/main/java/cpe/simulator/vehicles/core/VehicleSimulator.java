package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.RouteService;
import cpe.simulator.vehicles.api.UartGateway;
import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.domain.VehicleStatus;
import cpe.simulator.vehicles.uart.UartMessage;
import cpe.simulator.vehicles.uart.UartMessageParser;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
  private final long movingSendIntervalMs;
  private final long statusSendIntervalMs;
  private final long onSiteDurationMs;
  private final long baseSendJitterMs;
  private final String eventPosition;
  private final String eventVehicleStatus;
  private final String eventIncidentStatus;
  private final boolean logSends;
  private final UartMessageParser uartParser;
  private final RouteService routeService;
  private final boolean routeSnapStart;
  private final Map<String, Long> lastPositionSendMs = new HashMap<>();
  private final Map<String, Long> lastStatusSendMs = new HashMap<>();
  private final Map<String, VehicleStatus> lastSentStatus = new HashMap<>();
  private final Map<String, VehicleStatus> lastObservedStatus = new HashMap<>();
  private final Map<String, Long> returnRoutePendingMs = new HashMap<>();
  private final Map<String, Long> baseSendOffsetsMs = new HashMap<>();

  public VehicleSimulator(
      Fleet fleet,
      MovementModel movementModel,
      UartGateway uartGateway,
      UartMessageRouter router,
      Logger logger,
      Clock clock,
      long tickMs,
      long baseSendIntervalMs,
      long movingSendIntervalMs,
      long statusSendIntervalMs,
      long onSiteDurationMs,
      String eventPosition,
      String eventVehicleStatus,
      String eventIncidentStatus,
      boolean logSends,
      RouteService routeService,
      boolean routeSnapStart) {
    this.fleet = fleet;
    this.movementModel = movementModel;
    this.uartGateway = uartGateway;
    this.router = router;
    this.logger = logger;
    this.clock = clock;
    this.tickMs = tickMs;
    this.baseSendIntervalMs = baseSendIntervalMs;
    this.movingSendIntervalMs = movingSendIntervalMs;
    this.statusSendIntervalMs = statusSendIntervalMs;
    this.onSiteDurationMs = onSiteDurationMs;
    this.baseSendJitterMs = baseSendIntervalMs / 5;
    this.eventPosition = eventPosition;
    this.eventVehicleStatus = eventVehicleStatus;
    this.eventIncidentStatus = eventIncidentStatus;
    this.logSends = logSends;
    this.routeService = routeService;
    this.routeSnapStart = routeSnapStart;
    this.uartParser = new UartMessageParser();
  }

  public void run() {
    logger.info("Vehicules charges: " + fleet.size());
    initializeVehiclesNotAtBase();
    uartGateway.start(router);

    double tickSeconds = tickMs / 1_000.0;

    try {
      while (!Thread.currentThread().isInterrupted()) {
        long tickStart = System.nanoTime();
        long nowMs = clock.millis();
        long timestampSeconds = nowMs / 1_000L;

        for (VehicleSnapshot snapshot : fleet.advanceAll(movementModel, tickSeconds)) {
          logStatusChange(snapshot);
          handleStatusTransitions(snapshot, nowMs);
          sendPositionIfNeeded(snapshot, nowMs, timestampSeconds);
          sendStatusIfNeeded(snapshot, nowMs, timestampSeconds);
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

  private void handleStatusTransitions(VehicleSnapshot snapshot, long nowMs) {
    String immat = snapshot.immatriculation();
    GeoPoint target = snapshot.assignmentTarget();
    String incidentPhaseId = snapshot.incidentPhaseId();
    VehicleStatus status = snapshot.status();
    IncidentCoordinator coordinator = fleet.incidentCoordinator();

    if (target != null && incidentPhaseId != null && status == VehicleStatus.ENGAGE) {
      if (movementModel.isAtTarget(snapshot.position(), target)) {
        fleet.markArrivedAtTarget(immat, nowMs);
        coordinator.markArrived(immat, incidentPhaseId, nowMs, target);
        logger.info("Vehicule arrive sur intervention: " + immat);
      }
    }

    if (target != null && incidentPhaseId != null && status == VehicleStatus.SUR_INTERVENTION) {
      if (coordinator.canReturn(incidentPhaseId, nowMs, onSiteDurationMs)) {
        String lastVehicle = coordinator.getLastArrivedVehicle(incidentPhaseId);
        GeoPoint phaseTarget = coordinator.getTarget(incidentPhaseId);
        if (phaseTarget == null) {
          phaseTarget = target;
        }
        if (lastVehicle == null) {
          lastVehicle = immat;
        }
        for (String vehicleImmat : coordinator.getAssignedVehicles(incidentPhaseId)) {
          fleet.startReturn(vehicleImmat);
          returnRoutePendingMs.put(vehicleImmat, nowMs);
          logger.info("Vehicule quitte l'intervention: " + vehicleImmat);
        }
        sendToUart(
            eventIncidentStatus, 1, lastVehicle, phaseTarget, nowMs / 1_000L); // Tous sont partis
        coordinator.clearIncident(incidentPhaseId);
      }
    }

    if (status == VehicleStatus.RETOUR) {
      // Si une route de retour est en attente, la calculer et l'assigner
      if (returnRoutePendingMs.containsKey(immat)) {
        computeAndAssignReturnRoute(snapshot);
        returnRoutePendingMs.remove(immat);
      }

      GeoPoint base = snapshot.base();
      if (base != null && movementModel.isAtTarget(snapshot.position(), base)) {
        fleet.assignToBase(immat);
        logger.info("Vehicule de retour a la base: " + immat);
      }
    }
  }

  private void sendPositionIfNeeded(VehicleSnapshot snapshot, long nowMs, long timestampSeconds) {
    String immat = snapshot.immatriculation();
    long intervalMs = positionSendIntervalMs(snapshot);
    Long last = lastPositionSendMs.get(immat);

    if (last == null || nowMs - last >= intervalMs) {
      sendPositionMessage(snapshot, timestampSeconds);
      lastPositionSendMs.put(immat, nowMs);
    }
  }

  private void sendStatusIfNeeded(VehicleSnapshot snapshot, long nowMs, long timestampSeconds) {
    String immat = snapshot.immatriculation();
    VehicleStatus currentStatus = snapshot.status();
    VehicleStatus previousStatus = lastSentStatus.get(immat);
    Long lastSend = lastStatusSendMs.get(immat);

    boolean statusChanged = previousStatus != currentStatus;
    boolean intervalElapsed = lastSend == null || nowMs - lastSend >= statusSendIntervalMs;

    if (statusChanged || intervalElapsed) {
      sendToUart(eventVehicleStatus, snapshot.status().code(), snapshot.immatriculation(), snapshot.position(), timestampSeconds);
      lastStatusSendMs.put(immat, nowMs);
      lastSentStatus.put(immat, currentStatus);
    }
  }

  private void logStatusChange(VehicleSnapshot snapshot) {
    String immat = snapshot.immatriculation();
    VehicleStatus currentStatus = snapshot.status();
    VehicleStatus previousStatus = lastObservedStatus.put(immat, currentStatus);

    if (previousStatus != null && previousStatus != currentStatus) {
      logger.info(
          "Changement de status vehicule "
              + immat
              + ": "
              + previousStatus
              + " -> "
              + currentStatus);
    }
  }

  private void sendPositionMessage(VehicleSnapshot snapshot, long timestampSeconds) {
    UartMessage message =
        UartMessage.build(
            eventPosition,
            snapshot.status().code(),
            snapshot.immatriculation(),
            snapshot.position(),
            timestampSeconds);
    uartGateway.send(message);
    if (logSends) {
      logger.info("UART >> " + uartParser.serialize(message));
    }
  }

  private void sendToUart(String event, int status, String immatriculation, GeoPoint location, long timestampSeconds) {
    UartMessage message =
        UartMessage.build(event, status, immatriculation, location, timestampSeconds);
    uartGateway.send(message);
    if (logSends) {
      logger.info("UART >> " + uartParser.serialize(message));
    }
  }

  private long positionSendIntervalMs(VehicleSnapshot snapshot) {
    GeoPoint base = snapshot.base();
    if (base != null && movementModel.isAtTarget(snapshot.position(), base)) {
      return baseSendIntervalWithJitterMs(snapshot.immatriculation());
    }
    // Vehicle is moving (either to incident or returning to base)
    if (snapshot.assignmentTarget() != null || snapshot.status() == VehicleStatus.RETOUR) {
      return movingSendIntervalMs;
    }
    return baseSendIntervalMs;
  }

  private long baseSendIntervalWithJitterMs(String immatriculation) {
    if (baseSendJitterMs <= 0) {
      return baseSendIntervalMs;
    }
    Long offset = baseSendOffsetsMs.get(immatriculation);
    if (offset == null) {
      offset =
          ThreadLocalRandom.current().nextLong(-baseSendJitterMs, baseSendJitterMs + 1);
      baseSendOffsetsMs.put(immatriculation, offset);
    }
    long interval = baseSendIntervalMs + offset;
    return Math.max(1L, interval);
  }

  private void computeAndAssignReturnRoute(VehicleSnapshot snapshot) {
    String immat = snapshot.immatriculation();
    GeoPoint currentPosition = snapshot.position();
    GeoPoint base = snapshot.base();

    if (base == null) {
      logger.warn("Impossible de calculer route retour: base non definie pour " + immat);
      return;
    }

    try {
      RoutePlan returnPlan = routeService.computeRoute(currentPosition, base, routeSnapStart);
      fleet.startReturnWithRoute(immat, returnPlan);
      logger.info("Route retour calculee pour " + immat);
    } catch (Exception e) {
      logger.warn("Erreur calcul route retour pour " + immat + ": " + e.getMessage());
    }
  }

  private void initializeVehiclesNotAtBase() {
    for (VehicleSnapshot snapshot : fleet.snapshots()) {
      GeoPoint base = snapshot.base();
      GeoPoint position = snapshot.position();

      if (base == null) {
        logger.warn("Vehicule sans base definie: " + snapshot.immatriculation());
        continue;
      }

      if (!movementModel.isAtTarget(position, base)) {
        String immat = snapshot.immatriculation();
        logger.info("Vehicule non a sa base au demarrage, initiate retour: " + immat);
        fleet.startReturn(immat);
        returnRoutePendingMs.put(immat, clock.millis());
      }
    }
  }
}
