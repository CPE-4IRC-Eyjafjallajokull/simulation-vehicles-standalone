package cpe.simulator.vehicles.infrastructure.sdmis;

import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.VehicleAssignmentService;
import cpe.simulator.vehicles.infrastructure.http.HttpApiClient;
import java.io.IOException;

/** Service SDMIS pour recuperer la phase d'affectation d'un vehicule. */
public final class SdmisVehicleAssignmentService implements VehicleAssignmentService {

  private final HttpApiClient apiClient;
  private final Logger logger;

  public SdmisVehicleAssignmentService(HttpApiClient apiClient, Logger logger) {
    this.apiClient = apiClient;
    this.logger = logger;
  }

  @Override
  public String fetchIncidentPhaseId(String immatriculation)
      throws IOException, InterruptedException {
    if (immatriculation == null || immatriculation.isBlank()) {
      return null;
    }
    QGVehicleAssignmentRead response =
        apiClient.get(
            "/qg/vehicles/" + immatriculation + "/assignment", QGVehicleAssignmentRead.class);
    if (response == null) {
      logger.warn("Affectation introuvable pour " + immatriculation);
      return null;
    }
    return response.incidentPhaseId();
  }
}
