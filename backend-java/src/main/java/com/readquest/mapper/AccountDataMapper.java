package com.readquest.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

/** All identifiers come from this fixed server-side catalog. Values remain bound parameters. */
public interface AccountDataMapper {
  record Section(String name, String table, String key, String owner, String columns) {}

  List<Section> SECTIONS =
      List.of(
          new Section(
              "agentRuns",
              "agent_runs",
              "id",
              "user_id",
              "id,book_id,state,group_size,result_json,events_json,session_json,error_code,created_at"),
          new Section(
              "uploadedBooks",
              "books",
              "id",
              "owner_id",
              "id,title,author,status,visibility,moderation_status,word_count,total_chapters,reading_mode,metadata_json,created_at,updated_at"),
          new Section(
              "importJobs",
              "book_import_jobs",
              "id",
              "user_id",
              "id,book_id,status,error_message,created_at,updated_at"),
          new Section(
              "sceneImages",
              "scene_images",
              "id",
              "user_id",
              "id,book_id,retention_until,created_at"),
          new Section(
              "activationPurchases",
              "activation_purchases",
              "id",
              "user_id",
              "id,email,status,delivery_status,delivery_mode,code_expires_at,created_at,updated_at"),
          new Section(
              "remoteImportJobs",
              "remote_import_jobs",
              "id",
              "user_id",
              "id,display_url,title,kind,status,book_id,error_code,created_at,updated_at"),
          new Section(
              "progress",
              "reading_progress",
              "chapter_id",
              "user_id",
              "book_id,chapter_id,current_paragraph_index,completed_paragraphs,total_paragraphs,total_reading_ms,completed_at,updated_at"),
          new Section(
              "readingEvents",
              "reading_events",
              "event_id",
              "user_id",
              "event_id,book_id,chapter_id,current_paragraph_index,completed_paragraphs,total_paragraphs,reading_duration_ms,created_at"),
          new Section(
              "readingProfiles",
              "reading_profiles",
              "user_id",
              "user_id",
              "current_lexile,comfort_zone_min,comfort_zone_max,i_plus_one_min,i_plus_one_max,total_reading_minutes,average_lookup_per_sentence,average_comprehension_score,reading_confidence,last_adjustment_reason,last_adjusted_at,created_at,updated_at"),
          new Section(
              "calibrationSessions",
              "calibration_sessions",
              "id",
              "user_id",
              "id,status,current_step,estimated_lexile,final_profile_json,created_at,updated_at"),
          new Section(
              "calibrationResponses",
              "calibration_samples cs JOIN calibration_sessions s ON s.id=cs.session_id",
              "CONCAT(cs.session_id,':',cs.step)",
              "s.user_id",
              "cs.session_id,cs.step,cs.sample_id,cs.lexile,cs.text,cs.started_at,cs.finished_at,cs.reading_seconds,cs.lookup_count,cs.comprehension_score,cs.flow_score"),
          new Section(
              "dictionaries",
              "user_dictionaries",
              "id",
              "user_id",
              "id,display_name,original_file_name,byte_size,created_at,updated_at"),
          new Section(
              "shelf",
              "library_entries",
              "book_id",
              "user_id",
              "book_id,shelf_state,created_at,updated_at"),
          new Section(
              "notes",
              "book_notes",
              "id",
              "user_id",
              "id,book_id,chapter_id,paragraph_index,quote,note,created_at,updated_at"),
          new Section(
              "vocabulary",
              "lookup_items",
              "id",
              "user_id",
              "id,book_id,chapter_id,paragraph_id,paragraph_index,sentence_id,chunk_id,source_sentence_key,type,text,original_sentence,explanation,translation,grammar_analysis,reading_strategy,lookup_time,review_status,mastery_level,next_review_at"),
          new Section(
              "reviewCards",
              "review_cards",
              "id",
              "user_id",
              "id,book_id,chapter_id,lookup_item_id,kind,target_json,concept_json,fsrs_json,due_at,status,algorithm_version,created_at,updated_at"),
          new Section(
              "reviewSessions",
              "review_sessions",
              "id",
              "user_id",
              "id,group_size,current_index,total,state,current_stage,created_at,updated_at"),
          new Section(
              "reviewSessionCards",
              "review_session_cards sc JOIN review_sessions s ON s.id=sc.session_id",
              "CONCAT(sc.session_id,':',sc.position)",
              "s.user_id",
              "sc.session_id,sc.position,sc.card_id"),
          new Section(
              "reviewAttempts",
              "review_attempts",
              "id",
              "user_id",
              "id,session_id,card_id,answer_kind,stage,user_answer,response_time_ms,grading_state,grade_json,derived_rating,rating_applied,semantic_verdict,semantic_score,created_at"),
          new Section(
              "featureUsage",
              "feature_usage",
              "request_id",
              "user_id",
              "feature,occurred_at,result,units"),
          new Section(
              "entitlements",
              "user_entitlements",
              "id",
              "user_id",
              "id,source_order_no,code,label,starts_at,ends_at"),
          new Section(
              "sessions",
              "refresh_sessions",
              "id",
              "user_id",
              "id,expires_at,revoked_at,created_at"),
          new Section(
              "groupMemberships",
              "group_memberships",
              "id",
              "user_id",
              "id,book_id,role,muted_until,created_at,updated_at"),
          new Section(
              "groupMessages",
              "group_messages",
              "id",
              "sender_user_id",
              "id,book_id,body,client_message_id,deleted_at,created_at,updated_at"),
          new Section(
              "blocksCreated",
              "user_blocks",
              "blocked_user_id",
              "blocker_user_id",
              "blocked_user_id,created_at"),
          new Section(
              "reportsCreated",
              "content_reports",
              "id",
              "reporter_user_id",
              "id,book_id,target_type,target_message_id,target_user_id,reason,details,status,created_at,updated_at"),
          new Section(
              "governanceActions",
              "group_audit_events",
              "id",
              "actor_user_id",
              "id,book_id,action,target_type,target_id,reason,created_at"));

  @SelectProvider(type = Sql.class, method = "page")
  List<Map<String, Object>> page(
      @Param("section") String section, @Param("user") String user, @Param("after") String after);

  @DeleteProvider(type = Sql.class, method = "delete")
  int delete(@Param("table") String table, @Param("user") String user);

  class Sql {
    public static String page(Map<String, Object> args) {
      var s =
          SECTIONS.stream()
              .filter(value -> value.name().equals(args.get("section")))
              .findFirst()
              .orElseThrow();
      return "SELECT "
          + s.columns()
          + ", "
          + s.key()
          + " AS export_cursor FROM "
          + s.table()
          + " WHERE "
          + s.owner()
          + "=#{user} AND "
          + s.key()
          + ">#{after} ORDER BY "
          + s.key()
          + " LIMIT 250";
    }

    public static String delete(Map<String, Object> args) {
      String table = (String) args.get("table");
      if (!List.of(
              "agent_runs",
              "review_attempts",
              "review_sessions",
              "review_cards",
              "lookup_items",
              "book_notes",
              "library_entries",
              "reading_progress",
              "reading_events",
              "reading_profiles",
              "calibration_sessions",
              "feature_usage",
              "user_entitlements",
              "user_dictionaries",
              "ai_analysis_cache",
              "book_import_jobs",
              "remote_import_jobs")
          .contains(table)) throw new IllegalArgumentException("Unsupported account table");
      return "DELETE FROM " + table + " WHERE user_id=#{user}";
    }
  }
}
