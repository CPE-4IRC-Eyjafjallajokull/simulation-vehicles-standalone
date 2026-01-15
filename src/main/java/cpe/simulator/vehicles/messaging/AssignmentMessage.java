package cpe.simulator.vehicles.messaging;

/** Payload d'affectation recu depuis RabbitMQ. */
public record AssignmentMessage(String immatriculation, double latitude, double longitude) {}
