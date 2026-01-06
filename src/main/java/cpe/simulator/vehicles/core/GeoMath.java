package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;

/** Calculs geographiques simples pour la simulation. */
public final class GeoMath {

  private static final double EARTH_RADIUS_METERS = 6_371_000.0;

  private GeoMath() {}

  public static double distanceMeters(GeoPoint a, GeoPoint b) {
    double lat1 = Math.toRadians(a.latitude());
    double lat2 = Math.toRadians(b.latitude());
    double deltaLat = lat2 - lat1;
    double deltaLon = Math.toRadians(b.longitude() - a.longitude());

    double sinLat = Math.sin(deltaLat / 2.0);
    double sinLon = Math.sin(deltaLon / 2.0);

    double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
    double c = 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    return EARTH_RADIUS_METERS * c;
  }

  public static GeoPoint moveTowards(GeoPoint from, GeoPoint to, double stepMeters) {
    double distance = distanceMeters(from, to);
    if (distance <= 0.0 || stepMeters <= 0.0) {
      return from;
    }
    if (stepMeters >= distance) {
      return to;
    }

    double fraction = stepMeters / distance;
    double newLat = from.latitude() + (to.latitude() - from.latitude()) * fraction;
    double newLon = from.longitude() + (to.longitude() - from.longitude()) * fraction;
    return new GeoPoint(newLat, newLon);
  }
}
