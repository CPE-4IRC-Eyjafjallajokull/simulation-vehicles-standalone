package cpe.simulator.vehicles.api;

import cpe.simulator.vehicles.core.RoutePlan;
import cpe.simulator.vehicles.domain.GeoPoint;

/** Service de calcul d'itineraire. */
public interface RouteService {
  RoutePlan computeRoute(GeoPoint from, GeoPoint to, boolean snapStart)
      throws Exception;
}
