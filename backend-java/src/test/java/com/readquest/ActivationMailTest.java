package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.readquest.service.impl.ActivationMailImpl;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class ActivationMailTest {
  @TempDir Path directory;

  @Test
  void localPreviewWritesOnlyManagedFilesAndCanBeRemoved() throws Exception {
    var mail = new ActivationMailImpl(new MockEnvironment(), "local", true, directory.toString());
    String id = java.util.UUID.randomUUID().toString();
    mail.send(id, "reader@example.test", "RQ-ABCD-EFGH-JKLM");
    assertTrue(Files.readString(directory.resolve(id + ".eml")).contains("RQ-ABCD-EFGH-JKLM"));
    mail.removePreview(id);
    assertFalse(Files.exists(directory.resolve(id + ".eml")));
    assertThrows(
        IllegalArgumentException.class, () -> mail.send("../other", "reader@example.test", "code"));
  }

  @Test
  void localPreviewCannotBeEnabledOutsideLocalDevelopment() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ActivationMailImpl(new MockEnvironment(), "local", false, directory.toString()));
  }

  @Test
  void smtpRequiresCompleteCredentialsAndEncryptedTransport() {
    assertFalse(
        new ActivationMailImpl(new MockEnvironment(), "smtp", false, directory.toString())
            .configured());
    var env =
        new MockEnvironment()
            .withProperty("SMTP_HOST", "smtp.example.test")
            .withProperty("SMTP_USERNAME", "reader@example.test")
            .withProperty("SMTP_PASSWORD", "fake-local-secret")
            .withProperty("SMTP_SSL_ENABLED", "false");
    assertFalse(new ActivationMailImpl(env, "smtp", false, directory.toString()).configured());
  }
}
