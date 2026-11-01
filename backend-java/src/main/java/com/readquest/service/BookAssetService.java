package com.readquest.service;

public interface BookAssetService {
  record Asset(byte[] bytes, String contentType) {}

  Asset read(String book, String encodedPath, String token);
}
