package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.RouteService;
import cpe.simulator.vehicles.api.VehicleAssignmentService;
import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.uart.UartEventHandler;
import cpe.simulator.vehicles.uart.UartMessage;

/** Handler d'affectation de vehicule vers un incident. */
public final class AssignmentEventHandler implements UartEventHandler {

  private final String eventName;
  private final Fleet fleet;
  private final RouteService routeService;
  private final VehicleAssignmentService assignmentService;
  private final boolean snapStart;
  private final Logger logger;

  public AssignmentEventHandler(
      String eventName,
      Fleet fleet,
      RouteService routeService,
      VehicleAssignmentService assignmentService,
      boolean snapStart,
      Logger logger) {
    this.eventName = eventName;
    this.fleet = fleet;
    this.routeService = routeService;
    this.assignmentService = assignmentService;
    this.snapStart = snapStart;
    this.logger = logger;
  }

  @Override
  public String eventName() {
    return eventName;
  }

  @Override
  public void handle(UartMessage message) {
    GeoPoint target = new GeoPoint(message.latitude(), message.longitude());
    VehicleSnapshot snapshot = fleet.snapshotFor(message.immatriculation());
    if (snapshot == null) {
      logger.warn("Affectation ignoree, vehicule inconnu: " + message.immatriculation());
      return;
    }

    String incidentPhaseId;
    try {
      incidentPhaseId = assignmentService.fetchIncidentPhaseId(message.immatriculation());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn(
          "Affectation ignoree, recuperation phase interrompue: " + message.immatriculation());
      return;
    } catch (Exception e) {
      logger.warn(
          "Affectation ignoree, recuperation phase en echec pour "
              + message.immatriculation()
              + ": "
              + e.getMessage());
      return;
    }

    if (incidentPhaseId == null || incidentPhaseId.isBlank()) {
      logger.warn(
          "Affectation ignoree, phase manquante: " + message.immatriculation());
      return;
    }

    GeoPoint start = snapshot.position();
    RoutePlan plan = null;
    if (routeService != null && start != null) {
      try {
        plan = routeService.computeRoute(start, target, snapStart);
      } catch (Exception e) {
        logger.warn("Itineraire indisponible: " + e.getMessage());
      }
    }

    boolean updated =
        fleet.setAssignment(message.immatriculation(), target, plan, incidentPhaseId);
    if (!updated) {
      logger.warn(
          "Affectation ignoree, vehicule inconnu: " + message.immatriculation());
    }
  }
}
