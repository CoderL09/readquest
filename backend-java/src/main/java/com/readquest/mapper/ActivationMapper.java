package com.readquest.mapper;

import com.readquest.pojo.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ActivationMapper {
  @org.apache.ibatis.annotations.Update(
      "UPDATE activation_purchases SET delivery_status='failed',lease_id=NULL,lease_until=NULL,next_attempt_at=NULL WHERE attempts>=5 AND lease_until<CURRENT_TIMESTAMP(3) AND delivery_status IN ('pending','failed')")
  int expireExhausted();

  @org.apache.ibatis.annotations.Update(
      "UPDATE activation_purchases SET lease_id=#{lease},lease_until=#{until},attempts=attempts+1 WHERE id=#{id} AND status='paid' AND delivery_status IN ('pending','failed') AND sealed_code IS NOT NULL AND attempts<5 AND code_expires_at>CURRENT_TIMESTAMP(3) AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP(3)) AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP(3))")
  int claim(
      @Param("id") String id,
      @Param("lease") String lease,
      @Param("until") java.time.Instant until);

  @org.apache.ibatis.annotations.Update(
      "UPDATE activation_purchases SET delivery_status=#{status},next_attempt_at=#{next},lease_id=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND lease_id=#{lease}")
  int finish(
      @Param("id") String id,
      @Param("lease") String lease,
      @Param("status") String status,
      @Param("next") java.time.Instant next);

  ActivationPurchase find(@Param("user") String user, @Param("id") String id);

  ActivationPurchase idempotent(@Param("user") String user, @Param("key") String key);

  ActivationPurchase lock(String id);

  List<ActivationPurchase> list(String user);

  int recent(
      @Param("user") String user,
      @Param("emailHash") String emailHash,
      @Param("since") java.time.Instant since);

  void insert(ActivationPurchase purchase);

  void save(ActivationPurchase purchase);

  void insertCode(
      @Param("id") String id,
      @Param("hash") String hash,
      @Param("suffix") String suffix,
      @Param("user") String user,
      @Param("expires") java.time.Instant expires);

  List<ActivationPurchase> pending();

  @org.apache.ibatis.annotations.Select(
      "SELECT * FROM activation_purchases WHERE code_expires_at<=CURRENT_TIMESTAMP(3) AND"
          + " sealed_code IS NOT NULL ORDER BY code_expires_at LIMIT 100")
  List<ActivationPurchase> expired();

  void clearExpiredPayloads(String id);

  void revokeOwnedCodes(String user);

  void deleteOwned(String user);

  @org.apache.ibatis.annotations.Delete("DELETE FROM activation_purchases WHERE id=#{id}")
  void deleteOne(String id);
}
