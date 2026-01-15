package cpe.simulator.vehicles.api;

import cpe.simulator.vehicles.messaging.AssignmentMessage;

/** Callback pour les affectations recues via RabbitMQ. */
public interface AssignmentMessageListener {
  void onAssignment(AssignmentMessage message);
}
