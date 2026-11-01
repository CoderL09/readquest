package com.readquest.service;

import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface AccountService {
  StreamingResponseBody export(String user, String accessToken);

  Map<String, Object> requestDeletion(String user, String password);

  void processDeletions();
}
