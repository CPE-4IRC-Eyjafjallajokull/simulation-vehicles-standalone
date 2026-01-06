package cpe.simulator.vehicles.infrastructure.sdmis;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.RouteService;
import cpe.simulator.vehicles.core.RoutePlan;
import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.infrastructure.http.HttpApiClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Service de routage base sur l'API QG. */
public final class SdmisRouteService implements RouteService {

  private final HttpApiClient apiClient;
  private final Logger logger;

  public SdmisRouteService(HttpApiClient apiClient, Logger logger) {
    this.apiClient = apiClient;
    this.logger = logger;
  }

  @Override
  public RoutePlan computeRoute(GeoPoint from, GeoPoint to, boolean snapStart)
      throws IOException, InterruptedException {
    if (from == null || to == null) {
      return null;
    }

    RouteRequest request =
        new RouteRequest(
            new RoutePointRequest(from.latitude(), from.longitude()),
            new RoutePointRequest(to.latitude(), to.longitude()),
            snapStart);

    RouteResponse response = apiClient.post("/geo/route", request, RouteResponse.class);
    if (response == null || response.geometry() == null) {
      logger.warn("Itineraire absent de la reponse API");
      return null;
    }

    List<List<Double>> coordinates = response.geometry().coordinates();
    if (coordinates == null || coordinates.isEmpty()) {
      logger.warn("Itineraire vide retourne par l'API");
      return null;
    }

    List<GeoPoint> points = new ArrayList<>(coordinates.size());
    for (List<Double> coord : coordinates) {
      if (coord == null || coord.size() < 2) {
        continue;
      }
      Double lon = coord.get(0);
      Double lat = coord.get(1);
      if (lat == null || lon == null) {
        continue;
      }
      points.add(new GeoPoint(lat, lon));
    }

    if (points.isEmpty()) {
      logger.warn("Itineraire sans points valides");
      return null;
    }

    return new RoutePlan(points);
  }
}
