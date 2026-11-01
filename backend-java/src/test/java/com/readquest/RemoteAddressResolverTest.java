package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.imports.RemoteAddressResolver;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class RemoteAddressResolverTest {
  final ObjectMapper json = new ObjectMapper();

  @Test
  void dohAnswersRejectPrivateFakeMixedAndNonLiteralAddresses() throws Exception {
    for (String address :
        new String[] {
          "127.0.0.1", "169.254.169.254", "10.1.2.3", "198.18.0.145", "example.com", "192.168.1.1"
        }) {
      var records =
          json.createArrayNode()
              .add(json.createObjectNode().put("type", 1).put("data", "8.8.8.8"))
              .add(json.createObjectNode().put("type", 1).put("data", address));
      assertThrows(
          UnknownHostException.class, () -> RemoteAddressResolver.parsePublicAnswers(records));
    }
    assertThrows(
        UnknownHostException.class,
        () -> RemoteAddressResolver.parsePublicAnswers(json.createArrayNode()));
    assertThrows(IllegalArgumentException.class, () -> new RemoteAddressResolver(json, "unsafe"));
  }

  @Test
  void cnameIsNotUsedAsAnUnvalidatedConnectionAddress() throws Exception {
    var records =
        json.createArrayNode()
            .add(json.createObjectNode().put("type", 5).put("data", "cdn.example"))
            .add(json.createObjectNode().put("type", 1).put("data", "8.8.8.8"));
    assertEquals("8.8.8.8", RemoteAddressResolver.parsePublicAnswers(records)[0].getHostAddress());
  }
}
