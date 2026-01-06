package cpe.simulator.vehicles.core;

import cpe.simulator.vehicles.domain.GeoPoint;
import java.util.List;

/** Trajet pre-calcule a suivre, sous forme de points successifs. */
public final class RoutePlan {

  private final List<GeoPoint> points;
  private int nextIndex;

  public RoutePlan(List<GeoPoint> points) {
    this.points = List.copyOf(points);
    this.nextIndex = this.points.size() > 1 ? 1 : this.points.size();
  }

  public boolean isComplete() {
    return nextIndex >= points.size();
  }

  public GeoPoint advance(GeoPoint current, double stepMeters) {
    if (current == null || points.isEmpty() || stepMeters <= 0.0) {
      return current;
    }

    double remaining = stepMeters;
    GeoPoint position = current;

    while (remaining > 0.0 && nextIndex < points.size()) {
      GeoPoint target = points.get(nextIndex);
      double distance = GeoMath.distanceMeters(position, target);
      if (distance <= 0.0) {
        position = target;
        nextIndex++;
        continue;
      }
      if (remaining < distance) {
        position = GeoMath.moveTowards(position, target, remaining);
        remaining = 0.0;
      } else {
        position = target;
        remaining -= distance;
        nextIndex++;
      }
    }

    return position;
  }
}
