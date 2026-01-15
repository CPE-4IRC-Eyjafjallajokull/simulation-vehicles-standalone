package cpe.simulator.vehicles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.RouteService;
import cpe.simulator.vehicles.api.TelemetryGateway;
import cpe.simulator.vehicles.api.VehicleAssignmentService;
import cpe.simulator.vehicles.api.VehicleRepository;
import cpe.simulator.vehicles.config.SimulatorConfig;
import cpe.simulator.vehicles.core.AssignmentEventHandler;
import cpe.simulator.vehicles.core.Fleet;
import cpe.simulator.vehicles.core.MovementModel;
import cpe.simulator.vehicles.core.VehicleSimulator;
import cpe.simulator.vehicles.infrastructure.http.AuthStrategy;
import cpe.simulator.vehicles.infrastructure.http.HttpApiClient;
import cpe.simulator.vehicles.infrastructure.http.KeycloakAuthStrategy;
import cpe.simulator.vehicles.infrastructure.sdmis.SdmisVehicleRepository;
import cpe.simulator.vehicles.infrastructure.sdmis.SdmisRouteService;
import cpe.simulator.vehicles.infrastructure.sdmis.SdmisVehicleAssignmentService;
import cpe.simulator.vehicles.infrastructure.rabbitmq.RabbitMqTelemetryGateway;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Factory pour construire le simulateur de vehicules. */
public final class SimulatorFactory {

  private SimulatorFactory() {}

  public static VehicleSimulator create(SimulatorConfig config, Logger logger)
      throws IOException, InterruptedException {
    logger.info("Initialisation du simulateur de vehicules...");

    HttpClient httpClient = createHttpClient(config);
    ObjectMapper mapper = createObjectMapper();
    AuthStrategy auth = createAuthStrategy(config, httpClient, mapper);
    HttpApiClient apiClient =
        new HttpApiClient(config.apiBaseUrl(), config.apiTimeoutMs(), auth, httpClient, mapper);

    VehicleRepository repository = new SdmisVehicleRepository(apiClient, logger);
    Fleet fleet = new Fleet(repository.loadVehicles(), logger);

    TelemetryGateway telemetryGateway =
        new RabbitMqTelemetryGateway(
            config.rabbitmqDsn(),
            config.rabbitmqQueueTelemetry(),
            config.rabbitmqQueueAssignments(),
            config.rabbitmqQueueIncidentTelemetry(),
            config.rabbitmqEventPosition(),
            config.rabbitmqEventVehicleStatus(),
            config.rabbitmqEventIncidentStatus(),
            config.rabbitmqEventAssignment(),
            config.rabbitmqRetrySleepMs(),
            config.telemetryLogPublishes(),
            mapper,
            logger);

    RouteService routeService = new SdmisRouteService(apiClient, logger);
    VehicleAssignmentService assignmentService =
        new SdmisVehicleAssignmentService(apiClient, logger);

    AssignmentEventHandler assignmentHandler =
        new AssignmentEventHandler(
            fleet,
            routeService,
            assignmentService,
            config.routeSnapStart(),
            logger);

    MovementModel movementModel =
        new MovementModel(config.vehicleSpeedMps(), config.positionEpsilonMeters());

    return new VehicleSimulator(
        fleet,
        movementModel,
        telemetryGateway,
        assignmentHandler,
        logger,
        Clock.systemUTC(),
        config.simTickMs(),
        config.telemetryBaseSendIntervalMs(),
        config.telemetryMovingSendIntervalMs(),
        config.telemetryStatusSendIntervalMs(),
        config.onSiteDurationMs(),
        routeService,
        config.routeSnapStart());
  }

  private static HttpClient createHttpClient(SimulatorConfig config) {
    long connectTimeout = Math.min(config.keycloakTimeoutMs(), config.apiTimeoutMs());
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeout))
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  private static AuthStrategy createAuthStrategy(
      SimulatorConfig config, HttpClient httpClient, ObjectMapper mapper) {
    return new KeycloakAuthStrategy(
        config.keycloakIssuer(),
        config.keycloakClientId(),
        config.keycloakClientSecret(),
        config.keycloakTimeoutMs(),
        config.keycloakTokenExpirySkewSeconds(),
        httpClient,
        mapper,
        Clock.systemUTC());
  }
}
