package com.readquest.service;

public interface ActivationMail {
  String mode();

  boolean configured();

  void send(String purchaseId, String email, String code);

  void removePreview(String purchaseId);
}
