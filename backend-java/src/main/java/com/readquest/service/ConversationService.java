package com.readquest.service;

import com.readquest.dto.ConversationRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ConversationService {
  SseEmitter stream(String user, String bearer, String book, ConversationRequest request);
}
