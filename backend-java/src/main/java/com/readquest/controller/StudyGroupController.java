package com.readquest.controller;

import com.readquest.dto.StudyGroupRequests.*;
import com.readquest.exception.ApiException;
import com.readquest.service.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class StudyGroupController {
  private final StudyGroupService groups;
  private final GroupCoachService coach;

  public StudyGroupController(StudyGroupService groups, GroupCoachService coach) {
    this.groups = groups;
    this.coach = coach;
  }

  @GetMapping("/study-groups")
  public Object joined(Principal user) {
    return Map.of("groups", groups.joined(user.getName()));
  }

  @GetMapping("/study-groups/{book}")
  public Object summary(Principal user, @PathVariable String book) {
    return Map.of("group", groups.summary(user.getName(), book));
  }

  @PutMapping("/study-groups/{book}/membership")
  public ResponseEntity<?> join(Principal user, @PathVariable String book) {
    var result = groups.join(user.getName(), book);
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("created")) ? 201 : 200)
        .body(Map.of("membership", result.get("membership")));
  }

  @DeleteMapping("/study-groups/{book}/membership")
  public ResponseEntity<Void> leave(Principal user, @PathVariable String book) {
    groups.leave(user.getName(), book);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/study-groups/{book}/members")
  public Object members(
      Principal user,
      @PathVariable String book,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return groups.members(user.getName(), book, page, pageSize);
  }

  @GetMapping("/study-groups/{book}/members/{target}")
  public Object member(Principal user, @PathVariable String book, @PathVariable String target) {
    return Map.of("member", groups.member(user.getName(), book, target));
  }

  @PatchMapping("/study-groups/{book}/members/{target}")
  public ResponseEntity<Void> moderate(
      Principal user,
      @PathVariable String book,
      @PathVariable String target,
      @Valid @RequestBody Moderation in) {
    Instant until = null;
    if (in.mutedUntil() != null && !in.mutedUntil().isNull())
      try {
        if (!in.mutedUntil().isTextual()) throw new IllegalArgumentException();
        until = Instant.parse(in.mutedUntil().asText());
      } catch (Exception error) {
        throw new ApiException(422, "Invalid mute deadline");
      }
    groups.moderate(
        user.getName(), book, target, in.role(), until, in.mutedUntil() != null, in.reason());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/study-groups/{book}/members/{target}")
  public ResponseEntity<Void> remove(
      Principal user,
      @PathVariable String book,
      @PathVariable String target,
      @Valid @RequestBody Reason in) {
    groups.removeMember(user.getName(), book, target, in.reason());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/study-groups/{book}/messages")
  public Object messages(
      Principal user,
      @PathVariable String book,
      @RequestParam(required = false) String before,
      @RequestParam(required = false) String after,
      @RequestParam(defaultValue = "50") int limit) {
    return groups.messages(user.getName(), book, before, after, limit);
  }

  @PostMapping("/study-groups/{book}/messages")
  public ResponseEntity<?> send(
      Principal user, @PathVariable String book, @Valid @RequestBody Message in) {
    var result = groups.send(user.getName(), book, in.body(), in.clientMessageId());
    return ResponseEntity.status(Boolean.TRUE.equals(result.get("created")) ? 201 : 200)
        .body(Map.of("message", result.get("message")));
  }

  @DeleteMapping("/study-groups/{book}/messages/{id}")
  public ResponseEntity<Void> deleteMessage(
      Principal user,
      @PathVariable String book,
      @PathVariable String id,
      @Valid @RequestBody Reason in) {
    groups.deleteMessage(user.getName(), book, id, in.reason());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/study-groups/{book}/reports")
  public ResponseEntity<?> report(
      Principal user, @PathVariable String book, @Valid @RequestBody Report in) {
    return ResponseEntity.status(201)
        .body(
            Map.of(
                "report",
                groups.report(
                    user.getName(),
                    book,
                    in.targetType(),
                    in.targetMessageId(),
                    in.targetUserId(),
                    in.reason(),
                    in.details())));
  }

  @PutMapping("/users/me/blocks/{target}")
  public ResponseEntity<Void> block(
      Principal user, @PathVariable String target, @Valid @RequestBody Block in) {
    groups.block(user.getName(), in.bookId(), target);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/users/me/blocks/{target}")
  public ResponseEntity<Void> unblock(Principal user, @PathVariable String target) {
    groups.unblock(user.getName(), target);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/study-groups/{book}/events")
  public Object events(
      Principal user, @PathVariable String book, @RequestParam(required = false) String after) {
    return groups.events(user.getName(), book, after);
  }

  @PostMapping("/study-groups/{book}/english-assistance")
  public Object coach(Principal user, @PathVariable String book, @Valid @RequestBody Coach in) {
    return coach.assist(user.getName(), book, in.action(), in.text(), in.level());
  }
}
