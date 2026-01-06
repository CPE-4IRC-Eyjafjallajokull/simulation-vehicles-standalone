package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;

/** Etat mutable d'un vehicule pour la simulation. */
public final class VehicleState {

  private final String immatriculation;
  private final GeoPoint base;
  private GeoPoint position;
  private GeoPoint assignmentTarget;
  private RoutePlan routePlan;

  public VehicleState(String immatriculation, GeoPoint base, GeoPoint position) {
    this.immatriculation = immatriculation;
    this.base = base;
    this.position = position;
  }

  public String immatriculation() {
    return immatriculation;
  }

  public synchronized VehicleSnapshot snapshot() {
    return new VehicleSnapshot(immatriculation, position, base, assignmentTarget);
  }

  public synchronized void setAssignment(GeoPoint target, RoutePlan plan) {
    this.assignmentTarget = target;
    this.routePlan = plan;
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
    this.assignmentTarget = base;
    this.routePlan = null;
  }
}
