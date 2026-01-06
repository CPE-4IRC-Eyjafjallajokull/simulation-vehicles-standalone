package cpe.simulator.vehicles.api;

import cpe.simulator.vehicles.core.VehicleState;
import java.io.IOException;
import java.util.List;

/** Acces aux vehicules de reference (API SDMIS). */
public interface VehicleRepository {
  List<VehicleState> loadVehicles() throws IOException, InterruptedException;
}
