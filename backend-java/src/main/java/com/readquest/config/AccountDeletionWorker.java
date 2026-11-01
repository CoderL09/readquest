package com.readquest.config;

import com.readquest.service.AccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "readquest.maintenance.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AccountDeletionWorker {
  private final AccountService service;

  public AccountDeletionWorker(AccountService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 30000)
  public void sweep() {
    service.processDeletions();
  }
}
