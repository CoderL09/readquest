package com.readquest.service;

import java.net.URI;

public interface RemoteFetcher {
  record Resource(URI url, String contentType, String fileName, byte[] bytes) {}

  Resource fetch(String url);

  default Resource image(String url, java.util.Set<String> hosts) {
    throw new UnsupportedOperationException();
  }

  default Resource fetch(String url, java.util.Map<String, String> headers) {
    return fetch(url);
  }
}
