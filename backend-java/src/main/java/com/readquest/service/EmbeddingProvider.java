package com.readquest.service;

import java.util.List;

public interface EmbeddingProvider {
  boolean configured();

  String modelKey();

  List<double[]> embed(List<String> texts);
}
