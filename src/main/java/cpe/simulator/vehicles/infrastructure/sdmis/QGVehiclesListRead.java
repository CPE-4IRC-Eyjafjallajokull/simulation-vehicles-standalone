package cpe.simulator.vehicles.infrastructure.sdmis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGVehiclesListRead(List<QGVehicleDetail> vehicles, int total) {}
