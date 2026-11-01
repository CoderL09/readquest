package com.readquest.service;

import com.readquest.dto.CalibrationResponseInput;
import java.util.*;

public interface ProfileService {
  Map<String, Object> growth(String user);

  Map<String, Object> library(String user);

  Map<String, Object> passport(String user);

  Map<String, Object> metadata(String user, String id);

  Map<String, Object> startCalibration(String user);

  Map<String, Object> calibration(String user, String id);

  Map<String, Object> respond(String user, String id, int step, CalibrationResponseInput input);

  void adjustAfterProgress(String user);
}
