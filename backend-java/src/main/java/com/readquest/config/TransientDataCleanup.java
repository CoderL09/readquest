package com.readquest.config;

import com.readquest.mapper.AiMapper;
import com.readquest.mapper.MembershipMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "readquest.maintenance.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TransientDataCleanup {
  private final AiMapper ai;
  private final MembershipMapper membership;
  private final com.readquest.mapper.CatalogMapper catalog;

  public TransientDataCleanup(
      AiMapper a, MembershipMapper m, com.readquest.mapper.CatalogMapper catalog) {
    ai = a;
    membership = m;
    this.catalog = catalog;
  }

  @Scheduled(fixedDelayString = "${readquest.maintenance-delay-ms:3600000}")
  public void cleanup() {
    try {
      ai.cleanup();
      membership.cleanup();
      catalog.cleanup();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass()).error("Transient data cleanup failed", e);
    }
  }
}
