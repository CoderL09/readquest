package com.readquest.dto;

import com.readquest.exception.ApiException;

/** One pagination contract for offset-based list endpoints. */
public record PageQuery(int page, int pageSize) {
  public static final int MAX_OFFSET = 10000;

  public PageQuery {
    if (page < 1 || pageSize < 1 || pageSize > 100 || (page - 1L) * pageSize > MAX_OFFSET) {
      throw new ApiException(
          422, "INVALID_PAGINATION", "Use page >= 1, pageSize 1..100 and an offset at most 10000");
    }
  }

  public int offset() {
    return (page - 1) * pageSize;
  }
}
