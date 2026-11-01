package com.readquest.config;

import com.readquest.service.IngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "readquest.import-worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IngestionWorker {
  private final IngestionService service;

  public IngestionWorker(IngestionService s) {
    service = s;
  }

  @Scheduled(fixedDelayString = "${readquest.import-worker.delay-ms:2000}")
  public void recover() {
    try {
      service.processPending();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass()).error("Import recovery failed", e);
    }
  }
}
