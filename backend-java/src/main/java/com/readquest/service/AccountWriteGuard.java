package com.readquest.service;

import java.util.function.Supplier;

public interface AccountWriteGuard {
  <T> T execute(String user, Supplier<T> action);
}
