package com.readquest.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface GroupCoachService {
  JsonNode assist(String user, String book, String action, String text, String level);
}
