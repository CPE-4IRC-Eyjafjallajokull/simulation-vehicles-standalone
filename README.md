# Simulation Vehicles Standalone

Simulateur de vehicules pour alimenter le systeme SDMIS sans micro:bit. Il publie la telemetrie directement sur RabbitMQ et consomme les affectations depuis la queue `vehicle_assignments`.

## Fonctionnement
- Charge les vehicules via l'API SDMIS.
- Simule les deplacements, positions et changements de statut.
- Publie la telemetrie sur RabbitMQ (positions, statuts, fin d'intervention).
- Ecoute les affectations depuis RabbitMQ pour demarrer les interventions.

## Configuration (variables d'environnement)
Le fichier `.env` a la racine du projet est charge automatiquement (si present). Les variables du shell restent prioritaires.

### RabbitMQ (meme noms que la passerelle RF)
- `RABBITMQ_DSN`
- `RABBITMQ_QUEUE_TELEMETRY` (defaut `vehicle_telemetry`)
- `RABBITMQ_QUEUE_ASSIGNMENTS` (defaut `vehicle_assignments`)
- `RABBITMQ_QUEUE_INCIDENT_TELEMETRY` (defaut `incident_telemetry`)
- `RETRY_SLEEP` (en secondes, defaut `1.0`)

### SDMIS API
- `SDMIS_API_BASE_URL`
- `SDMIS_API_TIMEOUT_MS`

### Keycloak
- `KEYCLOAK_ISSUER`
- `KEYCLOAK_CLIENT_ID`
- `KEYCLOAK_CLIENT_SECRET`
- `KEYCLOAK_TIMEOUT_MS`
- `KEYCLOAK_TOKEN_EXPIRY_SKEW_SECONDS`

### Simulation
- `SIM_TICK_MS`
- `VEHICLE_SPEED_MPS`
- `POSITION_EPSILON_METERS`
- `TELEMETRY_BASE_SEND_INTERVAL_MS`
- `TELEMETRY_MOVING_SEND_INTERVAL_MS`
- `TELEMETRY_STATUS_SEND_INTERVAL_MS`
- `TELEMETRY_LOG_PUBLISHES`
- `ON_SITE_DURATION_MS`
- `ROUTE_SNAP_START`

### Evenements RabbitMQ (optionnel)
- `RABBITMQ_EVENT_POSITION` (defaut `vehicle_position_update`)
- `RABBITMQ_EVENT_VEHICLE_STATUS` (defaut `vehicle_status_update`)
- `RABBITMQ_EVENT_INCIDENT_STATUS` (defaut `incident_status_update`)
- `RABBITMQ_EVENT_ASSIGNMENT` (defaut `vehicle_assignment`)

## Format des messages RabbitMQ

### Telemetrie (queue `vehicle_telemetry`)
```json
{
  "event": "vehicle_position_update",
  "payload": {
    "immatriculation": "AA-123-AA",
    "latitude": 45.764043,
    "longitude": 4.835659,
    "timestamp": "2024-10-12T10:15:30Z"
  }
}
```

```json
{
  "event": "vehicle_status_update",
  "payload": {
    "immatriculation": "AA-123-AA",
    "status": 1,
    "timestamp": "2024-10-12T10:15:30Z"
  }
}
```

### Fin d'intervention (queue `incident_telemetry`)
```json
{
  "event": "incident_status_update",
  "payload": {
    "immatriculation": "AA-123-AA",
    "status": 1,
    "timestamp": "2024-10-12T10:15:30Z"
  }
}
```

### Affectation (queue `vehicle_assignments`)
```json
{
  "event": "vehicle_assignment",
  "payload": {
    "immatriculation": "AA-123-AA",
    "latitude": 45.764043,
    "longitude": 4.835659
  }
}
```

## Build et tests
```bash
mvn test
mvn clean package
```

## Execution
```bash
java -jar target/simulateur_java_vehicles_standalone-1.0-SNAPSHOT.jar
```

## Notes
- La telemetrie est publiee en JSON (compatible avec la passerelle RF).
- Le simulateur envoie `status` (numerique). L'API mappe vers les libelles.
