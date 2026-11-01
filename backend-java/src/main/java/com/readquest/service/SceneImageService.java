package com.readquest.service;

public interface SceneImageService {
  String generate(String user, String book, String prompt);

  byte[] read(String book, String id, String token);

  void deleteOwned(String user);

  void expire();
}
