package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.mapper.ReadingMapper;
import com.readquest.service.AccountWriteGuard;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountWriteGuardImpl implements AccountWriteGuard {
  private final ReadingMapper users;
  private final TransactionTemplate tx;

  public AccountWriteGuardImpl(ReadingMapper users, PlatformTransactionManager manager) {
    this.users = users;
    tx = new TransactionTemplate(manager);
  }

  public <T> T execute(String user, Supplier<T> action) {
    return tx.execute(status -> {
      if (users.lockUser(user) == null) throw new ApiException(403, "Account is not active");
      return action.get();
    });
  }
}
