package com.readquest.service;

import com.readquest.dto.Requests.*;
import com.readquest.vo.AuthResponse;

public interface AuthService {
  record SessionResult(AuthResponse response, String refreshToken) {}

  SessionResult register(Register in);

  SessionResult login(Login in);

  SessionResult refresh(String token);

  void logout(String token);

  AuthResponse.PublicUser current(String user);
}
