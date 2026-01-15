package cpe.simulator.vehicles.infrastructure.rabbitmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import cpe.simulator.vehicles.api.AssignmentMessageListener;
import cpe.simulator.vehicles.api.Logger;
import cpe.simulator.vehicles.api.TelemetryGateway;
import cpe.simulator.vehicles.domain.GeoPoint;
import cpe.simulator.vehicles.domain.VehicleStatus;
import cpe.simulator.vehicles.messaging.AssignmentMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Gateway RabbitMQ pour publier la telemetrie et recevoir les affectations. */
public final class RabbitMqTelemetryGateway implements TelemetryGateway {

  private static final AMQP.BasicProperties JSON_PROPERTIES =
      new AMQP.BasicProperties.Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .build();
  private static final long CONSUMER_POLL_MS = 500L;

  private final String rabbitmqDsn;
  private final String queueTelemetry;
  private final String queueAssignments;
  private final String queueIncidentTelemetry;
  private final String eventPosition;
  private final String eventVehicleStatus;
  private final String eventIncidentStatus;
  private final String eventAssignment;
  private final long retrySleepMs;
  private final boolean logPublishes;
  private final ObjectMapper mapper;
  private final Logger logger;
  private final ConnectionFactory factory;

  private volatile boolean running;
  private Thread consumerThread;
  private Connection publishConnection;
  private Channel publishChannel;
  private long nextPublishConnectMs;

  public RabbitMqTelemetryGateway(
      String rabbitmqDsn,
      String queueTelemetry,
      String queueAssignments,
      String queueIncidentTelemetry,
      String eventPosition,
      String eventVehicleStatus,
      String eventIncidentStatus,
      String eventAssignment,
      long retrySleepMs,
      boolean logPublishes,
      ObjectMapper mapper,
      Logger logger) {
    this.rabbitmqDsn = rabbitmqDsn;
    this.queueTelemetry = queueTelemetry;
    this.queueAssignments = queueAssignments;
    this.queueIncidentTelemetry = queueIncidentTelemetry;
    this.eventPosition = eventPosition;
    this.eventVehicleStatus = eventVehicleStatus;
    this.eventIncidentStatus = eventIncidentStatus;
    this.eventAssignment = eventAssignment;
    this.retrySleepMs = Math.max(250L, retrySleepMs);
    this.logPublishes = logPublishes;
    this.mapper = mapper;
    this.logger = logger;
    this.factory = new ConnectionFactory();
    try {
      this.factory.setUri(rabbitmqDsn);
      this.factory.setAutomaticRecoveryEnabled(true);
      this.factory.setTopologyRecoveryEnabled(true);
      this.factory.setNetworkRecoveryInterval(this.retrySleepMs);
    } catch (Exception e) {
      throw new IllegalArgumentException("RabbitMQ DSN invalide: " + rabbitmqDsn, e);
    }
  }

  @Override
  public void start(AssignmentMessageListener listener) {
    if (running) {
      return;
    }
    if (listener == null) {
      throw new IllegalArgumentException("Assignment listener manquant");
    }
    running = true;
    consumerThread = new Thread(() -> consumeLoop(listener), "rabbitmq-assignments");
    consumerThread.setDaemon(true);
    consumerThread.start();
  }

  @Override
  public void publishVehiclePosition(
      String immatriculation, GeoPoint position, long timestampSeconds) {
    if (immatriculation == null || immatriculation.isBlank() || position == null) {
      return;
    }
    Map<String, Object> payload = new HashMap<>();
    payload.put("immatriculation", immatriculation);
    payload.put("latitude", position.latitude());
    payload.put("longitude", position.longitude());
    payload.put("timestamp", Instant.ofEpochSecond(timestampSeconds).toString());
    publish(queueTelemetry, eventPosition, payload);
  }

  @Override
  public void publishVehicleStatus(
      String immatriculation, VehicleStatus status, long timestampSeconds) {
    if (immatriculation == null || immatriculation.isBlank() || status == null) {
      return;
    }
    Map<String, Object> payload = new HashMap<>();
    payload.put("immatriculation", immatriculation);
    payload.put("status", status.code());
    payload.put("timestamp", Instant.ofEpochSecond(timestampSeconds).toString());
    publish(queueTelemetry, eventVehicleStatus, payload);
  }

  @Override
  public void publishIncidentStatus(
      String immatriculation, int status, long timestampSeconds) {
    if (immatriculation == null || immatriculation.isBlank()) {
      return;
    }
    Map<String, Object> payload = new HashMap<>();
    payload.put("immatriculation", immatriculation);
    payload.put("status", status);
    payload.put("timestamp", Instant.ofEpochSecond(timestampSeconds).toString());
    publish(queueIncidentTelemetry, eventIncidentStatus, payload);
  }

  @Override
  public void close() {
    running = false;
    if (consumerThread != null) {
      consumerThread.interrupt();
      consumerThread = null;
    }
    closePublish();
  }

  private void consumeLoop(AssignmentMessageListener listener) {
    while (running) {
      try (Connection connection = factory.newConnection("sim-vehicles-consumer");
          Channel channel = connection.createChannel()) {
        declareQueues(channel);
        DeliverCallback callback =
            (tag, delivery) -> handleAssignment(listener, delivery.getBody());
        channel.basicConsume(queueAssignments, true, callback, tag -> {});
        logger.info("RabbitMQ assignments consumer actif: " + queueAssignments);

        while (running && connection.isOpen() && channel.isOpen()) {
          sleep(CONSUMER_POLL_MS);
        }
      } catch (Exception e) {
        if (running) {
          logger.warn("RabbitMQ consumer en echec: " + e.getMessage());
          sleep(retrySleepMs);
        }
      }
    }
  }

  private void publish(String queue, String event, Map<String, Object> payload) {
    Channel channel = ensurePublishChannel();
    if (channel == null) {
      return;
    }
    Map<String, Object> message = new HashMap<>();
    message.put("event", event);
    message.put("payload", payload);
    try {
      String json = mapper.writeValueAsString(message);
      channel.basicPublish("", queue, JSON_PROPERTIES, json.getBytes(StandardCharsets.UTF_8));
      if (logPublishes) {
        logger.info("RabbitMQ >> " + json);
      }
    } catch (Exception e) {
      logger.warn("RabbitMQ publish en echec: " + e.getMessage());
      closePublish();
    }
  }

  private Channel ensurePublishChannel() {
    if (publishChannel != null && publishChannel.isOpen()) {
      return publishChannel;
    }
    long now = System.currentTimeMillis();
    if (now < nextPublishConnectMs) {
      return null;
    }
    closePublish();
    try {
      publishConnection = factory.newConnection("sim-vehicles-publisher");
      publishChannel = publishConnection.createChannel();
      declareQueues(publishChannel);
      logger.info("RabbitMQ connecte: " + rabbitmqDsn);
      return publishChannel;
    } catch (Exception e) {
      logger.warn("RabbitMQ indisponible: " + e.getMessage());
      nextPublishConnectMs = now + retrySleepMs;
      closePublish();
      return null;
    }
  }

  private void declareQueues(Channel channel) throws Exception {
    channel.queueDeclare(queueTelemetry, true, false, false, null);
    channel.queueDeclare(queueAssignments, true, false, false, null);
    channel.queueDeclare(queueIncidentTelemetry, true, false, false, null);
  }

  private void closePublish() {
    if (publishChannel != null) {
      try {
        publishChannel.close();
      } catch (Exception ignored) {
      }
      publishChannel = null;
    }
    if (publishConnection != null) {
      try {
        publishConnection.close();
      } catch (Exception ignored) {
      }
      publishConnection = null;
    }
  }

  private void handleAssignment(AssignmentMessageListener listener, byte[] body) {
    if (body == null || body.length == 0) {
      return;
    }
    String payload = new String(body, StandardCharsets.UTF_8);
    try {
      JsonNode root = mapper.readTree(payload);
      String event = textValue(root.get("event"));
      if (event != null && !event.isBlank() && !event.equals(eventAssignment)) {
        logger.warn("Evenement RabbitMQ inconnu: " + event);
        return;
      }

      JsonNode messageNode = root.has("payload") ? root.get("payload") : root;
      AssignmentMessage message = parseAssignment(messageNode);
      if (message == null) {
        logger.warn("Affectation RabbitMQ invalide: " + payload);
        return;
      }
      listener.onAssignment(message);
    } catch (Exception e) {
      logger.warn("Erreur parsing affectation RabbitMQ: " + e.getMessage());
    }
  }

  private AssignmentMessage parseAssignment(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String immatriculation = textValue(node.get("immatriculation"));
    Double latitude = numberValue(node.get("latitude"));
    Double longitude = numberValue(node.get("longitude"));
    if (immatriculation == null || immatriculation.isBlank()) {
      return null;
    }
    if (latitude == null || longitude == null) {
      return null;
    }
    return new AssignmentMessage(immatriculation.trim(), latitude, longitude);
  }

  private static String textValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null ? null : value.trim();
  }

  private static Double numberValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isNumber()) {
      return node.doubleValue();
    }
    if (node.isTextual()) {
      try {
        return Double.parseDouble(node.asText().trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static void sleep(long delayMs) {
    if (delayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
