package com.readquest.service;

public interface TokenService {
  String accessToken(String user, String session);

  String authenticate(String token);

  String hash(String value);
}
