package cpe.simulator.vehicles.infrastructure.sdmis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteRequest(RoutePointRequest from, RoutePointRequest to, boolean snapStart) {}
