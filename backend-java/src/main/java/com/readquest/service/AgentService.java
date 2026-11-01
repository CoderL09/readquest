package com.readquest.service;

import com.readquest.dto.AgentRequest;
import java.util.Map;

public interface AgentService {
  java.util.List<Map<String, Object>> list(String user);

  Map<String, Object> create(String user, String key, AgentRequest input);

  Map<String, Object> get(String user, String id);

  Map<String, Object> confirm(String user, String id);
}
