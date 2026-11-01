package com.readquest.vo;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int pageSize, long total, boolean hasMore) {
  public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
    return new PageResult<>(items, page, size, total, (long) page * size < total);
  }
}
