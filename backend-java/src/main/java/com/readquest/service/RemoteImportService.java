package com.readquest.service;

import java.util.Map;

public interface RemoteImportService {
  Map<String, Object> inspect(String user, String url);

  Map<String, Object> create(String user, String url, String title, String kind);

  default Map<String, Object> create(
      String user, String url, String title, String kind, Map<String, Object> metadata) {
    return create(user, url, title, kind);
  }

  Map<String, Object> get(String user, String id);

  void processPending();
}
