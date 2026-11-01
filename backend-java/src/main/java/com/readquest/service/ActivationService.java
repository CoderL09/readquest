package com.readquest.service;

import java.util.*;

public interface ActivationService {
  Map<String, Object> settings();

  Map<String, Object> create(String user, String email, String requestKey);

  Map<String, Object> confirm(String user, String id);

  Map<String, Object> get(String user, String id);

  List<Map<String, Object>> list(String user);

  Map<String, Object> retry(String user, String id);

  void deliver();

  void cleanup(String user);
}
