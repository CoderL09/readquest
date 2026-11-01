package com.readquest.mapper;

import com.readquest.pojo.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MembershipMapper {
  User lockUser(String id);

  List<UserEntitlement> active(String user);

  UserEntitlement latest(String user);

  MembershipRedemptionCode lockCode(String hash);

  int consume(@Param("hash") String hash, @Param("user") String user);

  int insertEntitlement(UserEntitlement value);

  long usedUnits(
      @Param("user") String user, @Param("from") Instant from, @Param("until") Instant until);

  int record(FeatureUsage usage);

  int providerFailed(@Param("user") String user, @Param("request") String request);

  int hit(
      @Param("key") String key, @Param("window") long window, @Param("expires") Instant expires);

  int hits(String key);

  int cleanup();

  int updateDisplayName(@Param("user") String user, @Param("name") String name);

  List<RefreshSession> sessions(String user);

  int revokeSession(@Param("user") String user, @Param("id") String id);
}
