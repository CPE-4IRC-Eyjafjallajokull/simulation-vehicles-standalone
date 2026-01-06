package cpe.simulator.vehicles.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cpe.simulator.vehicles.domain.GeoPoint;
import org.junit.jupiter.api.Test;

class MovementModelTest {

  @Test
  void moveReducesDistanceToTarget() {
    MovementModel model = new MovementModel(10.0, 1.0);
    GeoPoint from = new GeoPoint(45.0, 5.0);
    GeoPoint to = new GeoPoint(45.0001, 5.0001);

    double before = GeoMath.distanceMeters(from, to);
    GeoPoint next = model.move(from, to, 1.0);
    double after = GeoMath.distanceMeters(next, to);

    assertTrue(after < before);
  }

  @Test
  void moveDoesNotOvershoot() {
    MovementModel model = new MovementModel(100.0, 1.0);
    GeoPoint from = new GeoPoint(45.0, 5.0);
    GeoPoint to = new GeoPoint(45.00001, 5.00001);

    GeoPoint next = model.move(from, to, 1.0);
    double distance = GeoMath.distanceMeters(next, to);

    assertTrue(distance <= 1.0);
  }
}
