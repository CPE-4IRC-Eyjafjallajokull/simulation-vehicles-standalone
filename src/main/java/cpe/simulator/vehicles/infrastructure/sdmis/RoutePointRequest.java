package cpe.simulator.vehicles.infrastructure.sdmis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutePointRequest(double latitude, double longitude) {}
