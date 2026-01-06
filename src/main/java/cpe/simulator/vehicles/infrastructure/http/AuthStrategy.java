package cpe.simulator.vehicles.infrastructure.http;

import java.net.http.HttpRequest;

/** Strategie d'authentification pour les appels HTTP. */
public interface AuthStrategy {
  void apply(HttpRequest.Builder builder);
}
