package com.readquest.service;

import com.readquest.pojo.FsrsState;
import java.time.Instant;

public interface FsrsScheduler {
  FsrsState initial(Instant now);

  FsrsState schedule(FsrsState card, Instant now, String verdict, boolean fuzz);

  double recall(FsrsState card, Instant now);
}
