package com.readquest.mapper;

import com.readquest.pojo.AgentRun;
import org.apache.ibatis.annotations.*;

public interface AgentMapper {
  @Select(
      "SELECT * FROM agent_runs WHERE user_id=#{user} ORDER BY created_at DESC,id DESC LIMIT 10")
  java.util.List<AgentRun> list(String user);

  @Select("SELECT * FROM agent_runs WHERE user_id=#{user} AND id=#{id}")
  AgentRun find(@Param("user") String user, @Param("id") String id);

  @Select("SELECT * FROM agent_runs WHERE user_id=#{user} AND request_key=#{key}")
  AgentRun previous(@Param("user") String user, @Param("key") String key);

  @Insert(
      "INSERT INTO agent_runs(id,user_id,request_key,input_hash,book_id,state,group_size,events_json,created_at) VALUES(#{id},#{userId},#{requestKey},#{inputHash},#{bookId},#{state},#{groupSize},'[]',CURRENT_TIMESTAMP(3))")
  void insert(AgentRun run);

  @Update(
      "UPDATE agent_runs SET state=#{state},result_json=#{resultJson},events_json=#{eventsJson},session_json=#{sessionJson},error_code=#{errorCode} WHERE id=#{id} AND user_id=#{userId}")
  void save(AgentRun run);

  @Update(
      "UPDATE agent_runs SET state='failed',error_code='RUN_INTERRUPTED' WHERE state='running' AND created_at<#{before}")
  int expire(java.time.Instant before);
}
