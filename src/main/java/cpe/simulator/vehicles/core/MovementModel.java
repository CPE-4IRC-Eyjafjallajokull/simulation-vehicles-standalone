package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;

/** Modele simple de mouvement vers une cible. */
public final class MovementModel {

  private final double speedMps;
  private final double epsilonMeters;

  public MovementModel(double speedMps, double epsilonMeters) {
    this.speedMps = speedMps;
    this.epsilonMeters = epsilonMeters;
  }

  public double speedMetersPerSecond() {
    return speedMps;
  }

  public GeoPoint move(GeoPoint current, GeoPoint target, double deltaSeconds) {
    if (target == null || current == null) {
      return current;
    }
    if (isAtTarget(current, target)) {
      return current;
    }
    double step = Math.max(0.0, speedMps * deltaSeconds);
    return GeoMath.moveTowards(current, target, step);
  }

  public boolean isAtTarget(GeoPoint current, GeoPoint target) {
    if (current == null || target == null) {
      return true;
    }
    return GeoMath.distanceMeters(current, target) <= epsilonMeters;
  }
}
