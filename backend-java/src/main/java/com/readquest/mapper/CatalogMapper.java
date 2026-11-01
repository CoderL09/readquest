package com.readquest.mapper;

import com.readquest.pojo.CatalogSnapshot;

public interface CatalogMapper {
  CatalogSnapshot snapshot(String key);

  int save(CatalogSnapshot value);

  int cleanup();
}
