package com.readquest.service;

import com.readquest.pojo.*;
import com.readquest.vo.AuthResponse;
import java.util.List;

public interface MembershipService {
  List<UserEntitlement> active(String user);

  UserEntitlement redeem(String user, String code);

  boolean admit(String user, String feature, String requestId);

  void providerFailed(String user, String request);

  boolean rateLimit(String scope, String identity, int limit, long windowSeconds);

  AuthResponse.PublicUser updateName(String user, String name);

  List<RefreshSession> sessions(String user);

  void revokeSession(String user, String id);
}
