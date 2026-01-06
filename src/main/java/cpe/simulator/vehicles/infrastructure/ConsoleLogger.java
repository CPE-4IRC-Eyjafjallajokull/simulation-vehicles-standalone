package cpe.simulator.vehicles.infrastructure;

import cpe.simulator.vehicles.api.Logger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Logger vers la console. */
public final class ConsoleLogger implements Logger {

  @Override
  public void info(String message) {
    System.out.println("[" + timestamp() + "] " + message);
  }

  @Override
  public void warn(String message) {
    System.out.println("[" + timestamp() + "] WARN " + message);
  }

  @Override
  public void error(String message) {
    System.err.println("[" + timestamp() + "] " + message);
  }

  private String timestamp() {
    return OffsetDateTime.now(ZoneOffset.UTC).toString();
  }
}
