package cpe.simulator.vehicles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.RouteService;
import cpe.simulator.vehicles.api.UartGateway;
import cpe.simulator.vehicles.api.VehicleAssignmentService;
import cpe.simulator.vehicles.api.VehicleRepository;
import cpe.simulator.vehicles.config.SimulatorConfig;
import cpe.simulator.vehicles.core.AssignmentEventHandler;
import cpe.simulator.vehicles.core.Fleet;
import cpe.simulator.vehicles.core.MovementModel;
import cpe.simulator.vehicles.core.UartMessageRouter;
import cpe.simulator.vehicles.core.VehicleSimulator;
import cpe.simulator.vehicles.infrastructure.http.AuthStrategy;
import cpe.simulator.vehicles.infrastructure.http.HttpApiClient;
import cpe.simulator.vehicles.infrastructure.http.KeycloakAuthStrategy;
import cpe.simulator.vehicles.infrastructure.sdmis.SdmisVehicleRepository;
import cpe.simulator.vehicles.infrastructure.sdmis.SdmisRouteService;
import cpe.simulator.vehicles.infrastructure.sdmis.SdmisVehicleAssignmentService;
import cpe.simulator.vehicles.infrastructure.uart.SerialUartGateway;
import cpe.simulator.vehicles.uart.UartMessageParser;
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

    UartMessageParser parser = new UartMessageParser();
    UartGateway uartGateway =
        new SerialUartGateway(
            config.uartPort(),
            config.uartBaud(),
            config.uartReconnectMs(),
            config.uartReadTimeoutMs(),
            config.uartWriteDelayMs(),
            parser,
            logger);

    RouteService routeService = new SdmisRouteService(apiClient, logger);
    VehicleAssignmentService assignmentService =
        new SdmisVehicleAssignmentService(apiClient, logger);

    UartMessageRouter router = new UartMessageRouter(logger);
    router.register(
        new AssignmentEventHandler(
            config.uartEventAffectation(),
            fleet,
            routeService,
            assignmentService,
            config.routeSnapStart(),
            logger));

    MovementModel movementModel =
        new MovementModel(config.vehicleSpeedMps(), config.positionEpsilonMeters());

    return new VehicleSimulator(
        fleet,
        movementModel,
        uartGateway,
        router,
        logger,
        Clock.systemUTC(),
        config.simTickMs(),
        config.uartBaseSendIntervalMs(),
        config.uartMovingSendIntervalMs(),
        config.uartStatusSendIntervalMs(),
        config.onSiteDurationMs(),
        config.uartEventPosition(),
        config.uartEventVehicleStatus(),
        config.uartEventIncidentStatus(),
        config.uartLogSends(),
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
