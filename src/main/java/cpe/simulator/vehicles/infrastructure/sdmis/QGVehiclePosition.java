package cpe.simulator.vehicles.infrastructure.sdmis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGVehiclePosition(Double latitude, Double longitude, Instant timestamp) {}
