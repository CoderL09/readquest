package com.readquest.mapper;

import com.readquest.pojo.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface AccountMapper {
  User lockUser(String user);

  AccountDeletionJob job(String user);

  AccountDeletionJob lockJob(String id);

  List<AccountDeletionJob> pending();

  int insert(AccountDeletionJob job);

  int block(String user);

  int revoke(String user);

  int closeBooks(String user);

  int anonymize(
      @Param("user") String user, @Param("email") String email, @Param("password") String password);

  int completed(String id);

  int failed(String id);
}
