package com.readquest.service;

public interface SceneImageProvider {
  boolean configured();

  byte[] generate(String prompt);
}
