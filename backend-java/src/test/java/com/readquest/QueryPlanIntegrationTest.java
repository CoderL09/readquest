package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Reproducible synthetic-data check; never points at the development database. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_APPLICATION_JSON", matches = ".*readquest_test.*")
class QueryPlanIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired SqlSessionFactory sessions;
  @Autowired ObjectMapper json;

  @Test
  void compareLibrarySortWithAndWithoutTheNewIndex() throws Exception {
    assertEquals("readquest_test", db.queryForObject("SELECT DATABASE()", String.class));
    String user = UUID.randomUUID().toString();
    int rows = 3000;
    db.update(
        "INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at) VALUES(?,?,'Synthetic query-plan reader','test-only-unusable',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        user + "@example.test");
    try {
      var books = new ArrayList<Object[]>();
      var entries = new ArrayList<Object[]>();
      for (int i = 0; i < rows; i++) {
        String id = UUID.randomUUID().toString();
        var time = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z").plusSeconds(i % 20));
        books.add(new Object[] {id, user, "Synthetic benchmark " + i, time, time});
        entries.add(new Object[] {user, id, time, time});
      }
      db.batchUpdate(
          "INSERT INTO books(id,owner_id,title,status,asset_scope,visibility,moderation_status,created_at,updated_at) VALUES(?,?,?,'completed','private','public','unreviewed',?,?)",
          books);
      db.batchUpdate(
          "INSERT INTO library_entries(user_id,book_id,shelf_state,created_at,updated_at) VALUES(?,?,'saved',?,?)",
          entries);
      db.execute("ANALYZE TABLE library_entries,books");
      String sql =
          sessions
              .getConfiguration()
              .getMappedStatement("com.readquest.mapper.LibraryMapper.list")
              .getBoundSql(Map.of("userId", user, "limit", 20, "offset", 0))
              .getSql();
      String baseline =
          sql.replace(
              "library_entries l JOIN", "library_entries l IGNORE INDEX (library_user_order) JOIN");
      assertNotEquals(sql, baseline);
      JsonNode before =
          json.readTree(
              db.queryForObject("EXPLAIN FORMAT=JSON " + baseline, String.class, user, 20, 0));
      JsonNode after =
          json.readTree(db.queryForObject("EXPLAIN FORMAT=JSON " + sql, String.class, user, 20, 0));
      var report =
          Map.of(
              "rows",
              rows,
              "before",
              before,
              "after",
              after,
              "beforeUsesFilesort",
              before.findValues("using_filesort").stream().anyMatch(JsonNode::asBoolean),
              "afterUsesFilesort",
              after.findValues("using_filesort").stream().anyMatch(JsonNode::asBoolean),
              "scope",
              "Synthetic rows in dedicated test database; baseline ignores only V21 library index");
      Path output = Path.of("target/optimization/query-plan.json");
      Files.createDirectories(output.getParent());
      Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
      assertEquals(20, db.queryForList(sql, user, 20, 0).size());
      assertTrue(after.toString().contains("library_user_order"));
    } finally {
      db.update("DELETE FROM library_entries WHERE user_id=?", user);
      db.update("DELETE FROM books WHERE owner_id=?", user);
      db.update("DELETE FROM users WHERE id=?", user);
    }
  }
}
