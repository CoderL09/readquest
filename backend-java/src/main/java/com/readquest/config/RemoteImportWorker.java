package com.readquest.config;

import com.readquest.service.RemoteImportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "readquest.import-worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RemoteImportWorker {
  private final RemoteImportService service;

  public RemoteImportWorker(RemoteImportService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 2000)
  public void work() {
    service.processPending();
  }
}
