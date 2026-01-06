package cpe.simulator.vehicles.infrastructure.sdmis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QGVehicleDetail(
    String immatriculation,
    QGBaseInterestPoint baseInterestPoint,
    QGVehiclePosition currentPosition) {}
