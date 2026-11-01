package com.readquest.service;

import java.util.*;

public interface CatalogService {
  List<Map<String, Object>> sources();

  List<Map<String, Object>> categories();

  Map<String, Object> search(
      String identity, String query, String category, String provider, String cursor, int limit);

  Map<String, Object> trending(String identity, String window, String provider, String category);

  Map<String, Object> importOffer(String user, String provider, String external, String offer);
}
