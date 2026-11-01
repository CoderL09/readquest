package com.readquest.mapper;

import com.readquest.pojo.SceneImage;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface SceneImageMapper {
  @Insert(
      "INSERT INTO scene_images(id,user_id,book_id,file_name,retention_until,created_at)"
          + " VALUES(#{id},#{userId},#{bookId},#{fileName},#{retentionUntil},#{createdAt})")
  void insert(SceneImage value);

  @Select("SELECT * FROM scene_images WHERE id=#{id}")
  SceneImage find(String id);

  @Select("SELECT * FROM scene_images WHERE user_id=#{user} ORDER BY id LIMIT 100")
  List<SceneImage> owned(String user);

  @Select(
      "SELECT * FROM scene_images WHERE retention_until<=CURRENT_TIMESTAMP ORDER BY retention_until"
          + " LIMIT 100")
  List<SceneImage> expired();

  @Delete("DELETE FROM scene_images WHERE id=#{id}")
  void delete(String id);
}
