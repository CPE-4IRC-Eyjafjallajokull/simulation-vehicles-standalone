package cpe.simulator.vehicles.uart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UartMessageParserTest {

  private final UartMessageParser parser = new UartMessageParser();

  @Test
  void parseValidFrame() {
    UartMessage message = parser.parse("vehicle_affectation,1,FY089KV,47.4309,4.5874,1234");
    assertEquals("vehicle_affectation", message.event());
    assertEquals(1, message.status());
    assertEquals("FY-089-KV", message.immatriculation());
    assertEquals(47.4309, message.latitude(), 0.000001);
    assertEquals(4.5874, message.longitude(), 0.000001);
    assertEquals(1234L, message.timestampSeconds());
  }

  @Test
  void parseRejectsBadFrame() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("bad,frame"));
  }

  @Test
  void parseRejectsTimestampOutOfRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse("event,0,AA123,0.0,0.0,4294967296"));
  }

  @Test
  void serializeFormatsCsv() {
    UartMessage message = new UartMessage("vehicle_position", 1, "FY-089-KV", 1.2, 3.4, 42L);
    assertEquals("vehicle_position,1,FY089KV,1.200000,3.400000,42", parser.serialize(message));
  }
}
