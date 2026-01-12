package cpe.simulator.vehicles.api;

import java.io.IOException;

/** Acces aux affectations vehicules (API SDMIS). */
public interface VehicleAssignmentService {
  String fetchIncidentPhaseId(String immatriculation) throws IOException, InterruptedException;
}
