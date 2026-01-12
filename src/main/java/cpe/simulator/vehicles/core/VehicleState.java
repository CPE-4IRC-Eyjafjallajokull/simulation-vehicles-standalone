package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.domain.VehicleStatus;

/** Etat mutable d'un vehicule pour la simulation. */
public final class VehicleState {

  private final String immatriculation;
  private final GeoPoint base;
  private GeoPoint position;
  private GeoPoint assignmentTarget;
  private String incidentPhaseId;
  private RoutePlan routePlan;
  private VehicleStatus status = VehicleStatus.DISPONIBLE;
  private long arrivedAtTargetMs = -1;

  public VehicleState(String immatriculation, GeoPoint base, GeoPoint position) {
    this.immatriculation = immatriculation;
    this.base = base;
    this.position = position;
  }

  public String immatriculation() {
    return immatriculation;
  }

  public synchronized VehicleSnapshot snapshot() {
    return new VehicleSnapshot(
        immatriculation, position, base, assignmentTarget, incidentPhaseId, status, arrivedAtTargetMs);
  }

  public synchronized void setAssignment(GeoPoint target, RoutePlan plan, String incidentPhaseId) {
    this.assignmentTarget = target;
    this.routePlan = plan;
    this.incidentPhaseId = incidentPhaseId;
    this.status = VehicleStatus.ENGAGE;
    this.arrivedAtTargetMs = -1;
  }

  public synchronized void setStatus(VehicleStatus newStatus) {
    this.status = newStatus;
  }

  public synchronized void markArrivedAtTarget(long timestampMs) {
    if (arrivedAtTargetMs < 0) {
      arrivedAtTargetMs = timestampMs;
      status = VehicleStatus.SUR_INTERVENTION;
    }
  }

  public synchronized void startReturn() {
    status = VehicleStatus.RETOUR;
    arrivedAtTargetMs = -1;
    // Ne pas modifier assignmentTarget ni routePlan ici
    // Ils vont être mis à jour par startReturnWithRoute si une route est disponible
  }

  public synchronized void startReturnWithRoute(RoutePlan returnPlan) {
    this.routePlan = returnPlan;
    this.assignmentTarget = base;
    this.status = VehicleStatus.RETOUR;
    this.arrivedAtTargetMs = -1;
  }

  public synchronized VehicleSnapshot advance(MovementModel model, double deltaSeconds) {
    if (routePlan != null && !routePlan.isComplete()) {
      double step = Math.max(0.0, model.speedMetersPerSecond() * deltaSeconds);
      position = routePlan.advance(position, step);
      if (routePlan.isComplete()) {
        routePlan = null;
      }
      return snapshot();
    }

    GeoPoint target = assignmentTarget;
    if (target == null && base != null && !model.isAtTarget(position, base)) {
      target = base;
    }
    if (target != null) {
      position = model.move(position, target, deltaSeconds);
    }
    return snapshot();
  }

  public synchronized void assignToBase() {
    this.assignmentTarget = null;
    this.routePlan = null;
    this.incidentPhaseId = null;
    this.status = VehicleStatus.DISPONIBLE;
    this.arrivedAtTargetMs = -1;
  }
}
