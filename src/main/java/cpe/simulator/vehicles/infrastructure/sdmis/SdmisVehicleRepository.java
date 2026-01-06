package cpe.simulator.vehicles.infrastructure.sdmis;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.VehicleRepository;
import cpe.simulator.vehicles.core.VehicleState;
import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.infrastructure.http.HttpApiClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Repository SDMIS pour charger les vehicules depuis l'API QG. */
public final class SdmisVehicleRepository implements VehicleRepository {

  private final HttpApiClient apiClient;
  private final Logger logger;

  public SdmisVehicleRepository(HttpApiClient apiClient, Logger logger) {
    this.apiClient = apiClient;
    this.logger = logger;
  }

  @Override
  public List<VehicleState> loadVehicles() throws IOException, InterruptedException {
    QGVehiclesListRead response = apiClient.get("/qg/vehicles", QGVehiclesListRead.class);
    List<QGVehicleDetail> vehicles = response.vehicles() == null ? List.of() : response.vehicles();

    List<VehicleState> results = new ArrayList<>(vehicles.size());
    for (QGVehicleDetail detail : vehicles) {
      String immatriculation = detail.immatriculation();
      if (immatriculation == null || immatriculation.isBlank()) {
        logger.warn("Vehicule ignore (immatriculation manquante)");
        continue;
      }

      QGBaseInterestPoint base = detail.baseInterestPoint();
      if (base == null || base.latitude() == null || base.longitude() == null) {
        logger.warn("Vehicule ignore (base_interest_point manquant): " + immatriculation);
        continue;
      }

      GeoPoint basePoint = new GeoPoint(base.latitude(), base.longitude());
      GeoPoint position = basePoint;

      QGVehiclePosition current = detail.currentPosition();
      if (current != null && current.latitude() != null && current.longitude() != null) {
        position = new GeoPoint(current.latitude(), current.longitude());
      }

      results.add(new VehicleState(immatriculation, basePoint, position));
    }

    logger.info("Vehicules valides: " + results.size() + "/" + response.total());
    return results;
  }
}
