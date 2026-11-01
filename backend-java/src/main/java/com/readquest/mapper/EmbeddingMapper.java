package com.readquest.mapper;

import com.readquest.pojo.BookEmbedding;
import org.apache.ibatis.annotations.*;

public interface EmbeddingMapper {
  @Select("SELECT vector_json FROM book_embeddings WHERE book_id=#{book} AND cache_key=#{key}")
  String find(@Param("book") String book, @Param("key") String key);

  @Insert(
      "INSERT INTO book_embeddings(book_id,cache_key,vector_json,created_at)"
          + " VALUES(#{bookId},#{cacheKey},#{vectorJson},CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE"
          + " vector_json=VALUES(vector_json)")
  void save(BookEmbedding value);

  @Delete(
      "DELETE FROM book_embeddings WHERE book_id IN (SELECT id FROM books WHERE owner_id=#{user})")
  void deleteOwned(String user);
}
