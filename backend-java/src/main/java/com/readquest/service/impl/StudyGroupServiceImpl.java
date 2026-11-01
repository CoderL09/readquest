package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.groups.GroupLanguage;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import com.readquest.vo.GroupProgress;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StudyGroupServiceImpl implements StudyGroupService {
  private final StudyGroupMapper mapper;
  private final UserMapper users;
  private final BookService books;
  private final AccountWriteGuard guard;
  private final MembershipService limits;
  private final GroupLanguage language;

  public StudyGroupServiceImpl(
      StudyGroupMapper mapper,
      UserMapper users,
      BookService books,
      AccountWriteGuard guard,
      MembershipService limits,
      GroupLanguage language) {
    this.mapper = mapper;
    this.users = users;
    this.books = books;
    this.guard = guard;
    this.limits = limits;
    this.language = language;
  }

  private static Map<String, Object> map(Object... values) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) result.put(values[i].toString(), values[i + 1]);
    return result;
  }

  private Book available(String user, String book) {
    var found = mapper.lockBook(book);
    if (found == null) throw new ApiException(404, "Study group not found");
    books.requireReadable(user, book);
    return found;
  }

  private StudyGroupMember require(String user, String book) {
    available(user, book);
    var member = mapper.member(book, user);
    if (member == null)
      throw new ApiException(403, "MEMBERSHIP_REQUIRED", "Study group membership required");
    return member;
  }

  public void assertMember(String user, String book) {
    guard.execute(
        user,
        () -> {
          require(user, book);
          return null;
        });
  }

  private void audit(
      String user, String book, String action, String type, String target, String reason) {
    mapper.audit(
        new StudyGroupAudit(
            UUID.randomUUID().toString(), book, user, action, type, target, reason, Instant.now()));
  }

  private Map<String, Object> summaryView(String user, String book) {
    var source = books.get(user, book);
    var membership = mapper.member(book, user);
    return map(
        "bookId",
        book,
        "title",
        source.getTitle(),
        "author",
        source.getAuthor(),
        "coverUrl",
        Objects.toString(source.getCoverUrl(), ""),
        "memberCount",
        mapper.countMembers(book),
        "joined",
        membership != null,
        "joinedAt",
        membership == null ? null : membership.createdAt());
  }

  public Map<String, Object> summary(String user, String book) {
    return guard.execute(
        user,
        () -> {
          available(user, book);
          return summaryView(user, book);
        });
  }

  public List<Map<String, Object>> joined(String user) {
    return guard.execute(
        user,
        () -> {
          var ids = mapper.joined(user);
          new TreeSet<>(ids).forEach(mapper::lockBook);
          var result = new ArrayList<Map<String, Object>>();
          for (String book : ids)
            try {
              available(user, book);
              result.add(summaryView(user, book));
            } catch (ApiException error) {
              if (error.status() != 404) throw error;
            }
          return result;
        });
  }

  public Map<String, Object> join(String user, String book) {
    return guard.execute(
        user,
        () -> {
          var source = available(user, book);
          var existing = mapper.member(book, user);
          boolean admin = user.equals(source.getOwnerId()) || mapper.adminCount(book) == 0;
          if (existing != null) {
            if (admin && !existing.role().equals("admin")) {
              mapper.role(book, user, "admin");
              audit(
                  user,
                  book,
                  "member.admin_promoted_on_join",
                  "member",
                  user,
                  "Existing member repaired administrator access");
            }
            return map(
                "membership",
                map("bookId", book, "joinedAt", existing.createdAt()),
                "created",
                false);
          }
          Instant now = Instant.now();
          mapper.insertMember(
              new StudyGroupMember(
                  UUID.randomUUID().toString(),
                  book,
                  user,
                  admin ? "admin" : "member",
                  null,
                  now,
                  now));
          mapper.memberEvents(book, user);
          if (admin)
            audit(
                user,
                book,
                "member.admin_assigned_on_join",
                "member",
                user,
                "First explicit member or uploader received administrator access");
          return map("membership", map("bookId", book, "joinedAt", now), "created", true);
        });
  }

  private void leaveLocked(String user, String book, boolean closed) {
    var leaving = mapper.memberAny(book, user);
    if (leaving == null) return;
    mapper.removeMember(book, user);
    mapper.memberEvents(book, user);
    if (!closed && leaving.role().equals("admin") && mapper.adminCount(book) == 0) {
      var remaining = mapper.members(book, 0, 1);
      if (!remaining.isEmpty()) {
        String successor = remaining.getFirst().userId();
        mapper.role(book, successor, "admin");
        audit(
            user,
            book,
            "member.admin_transferred_on_leave",
            "member",
            successor,
            "Administrator access transferred to earliest active member");
      }
    }
  }

  private boolean open(Book book) {
    return book != null
        && "completed".equals(book.getStatus())
        && "public".equals(book.getVisibility())
        && Set.of("unreviewed", "approved").contains(book.getModerationStatus())
        && Set.of("private", "verified-shared").contains(book.getAssetScope());
  }

  public void leave(String user, String book) {
    guard.execute(
        user,
        () -> {
          var source = mapper.lockBook(book);
          leaveLocked(user, book, !open(source));
          return null;
        });
  }

  private Map<String, Object> memberView(
      String viewer, StudyGroupMember membership, GroupProgress progress, long total) {
    var user = users.findById(membership.userId());
    long completed = progress == null ? 0 : progress.completedParagraphs(),
        paragraphs = progress == null ? 0 : progress.totalParagraphs();
    int percent =
        paragraphs <= 0 || completed <= 0
            ? 0
            : (int) Math.min(100, Math.round(100.0 * completed / paragraphs));
    return map(
        "userId",
        membership.userId(),
        "displayName",
        user.getDisplayName(),
        "avatarUrl",
        Objects.toString(user.getAvatarUrl(), ""),
        "joinedAt",
        membership.createdAt(),
        "isCurrentUser",
        viewer.equals(membership.userId()),
        "role",
        membership.role(),
        "mutedUntil",
        membership.mutedUntil(),
        "blockedByCurrentUser",
        mapper.blocked(viewer, membership.userId()),
        "percent",
        percent,
        "completedChapters",
        progress == null ? 0 : progress.completedChapters(),
        "totalChapters",
        total,
        "lastActiveAt",
        progress == null ? null : progress.lastActiveAt());
  }

  public Map<String, Object> members(String user, String book, int page, int size) {
    if (page < 1 || page > 10000 || size < 1 || size > 50)
      throw new ApiException(422, "Invalid member pagination");
    return guard.execute(
        user,
        () -> {
          require(user, book);
          var memberships = mapper.members(book, (page - 1) * size, size);
          var progress = new HashMap<String, GroupProgress>();
          if (!memberships.isEmpty())
            for (var item :
                mapper.progress(book, memberships.stream().map(StudyGroupMember::userId).toList()))
              progress.put(item.userId(), item);
          long chapters = mapper.totalChapters(book), count = mapper.countMembers(book);
          var result =
              memberships.stream()
                  .map(member -> memberView(user, member, progress.get(member.userId()), chapters))
                  .toList();
          return map(
              "members",
              result,
              "pagination",
              map(
                  "page",
                  page,
                  "pageSize",
                  size,
                  "total",
                  count,
                  "totalPages",
                  (count + size - 1) / size,
                  "hasMore",
                  (long) page * size < count));
        });
  }

  public Map<String, Object> member(String user, String book, String target) {
    return guard.execute(
        user,
        () -> {
          require(user, book);
          var membership = mapper.member(book, target);
          if (membership == null) throw new ApiException(404, "Member not found");
          var progress = mapper.progress(book, List.of(target));
          return memberView(
              user,
              membership,
              progress.isEmpty() ? null : progress.getFirst(),
              mapper.totalChapters(book));
        });
  }

  private Map<String, Object> messageView(
      String viewer, StudyGroupMessage value, Map<String, Map<String, Object>> senders) {
    var sender =
        senders.computeIfAbsent(
            value.getSenderUserId(),
            id -> {
              var membership = mapper.member(value.getBookId(), id);
              var person = membership == null ? null : users.findById(id);
              return person == null
                  ? map("userId", "", "displayName", "Former member", "avatarUrl", "")
                  : map(
                      "userId",
                      id,
                      "displayName",
                      person.getDisplayName(),
                      "avatarUrl",
                      Objects.toString(person.getAvatarUrl(), ""));
            });
    return map(
        "id",
        value.getId(),
        "bookId",
        value.getBookId(),
        "sender",
        sender,
        "body",
        value.getDeletedAt() == null ? value.getBody() : "",
        "clientMessageId",
        value.getClientMessageId(),
        "createdAt",
        value.getCreatedAt(),
        "isCurrentUser",
        viewer.equals(sender.get("userId")),
        "deleted",
        value.getDeletedAt() != null,
        "deletedAt",
        value.getDeletedAt());
  }

  private Long cursor(String book, String id) {
    if (id == null) return null;
    var value = mapper.message(book, id);
    if (value == null) throw new ApiException(422, "Invalid message cursor");
    return value.getSequence();
  }

  public Map<String, Object> messages(
      String user, String book, String before, String after, int limit) {
    if (limit < 1 || limit > 100 || before != null && after != null)
      throw new ApiException(422, "Invalid message query");
    return guard.execute(
        user,
        () -> {
          require(user, book);
          var rows =
              mapper.messages(book, user, cursor(book, before), cursor(book, after), limit + 1);
          boolean more = rows.size() > limit;
          var selected = new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
          if (after == null) Collections.reverse(selected);
          var senders = new HashMap<String, Map<String, Object>>();
          return map(
              "messages",
              selected.stream().map(value -> messageView(user, value, senders)).toList(),
              "pageInfo",
              map(
                  "hasMore",
                  more,
                  "oldestCursor",
                  selected.isEmpty() ? null : selected.getFirst().getId(),
                  "newestCursor",
                  selected.isEmpty() ? null : selected.getLast().getId()));
        });
  }

  public Map<String, Object> send(String user, String book, String body, String client) {
    if (body == null
        || body.strip().isEmpty()
        || body.length() > 1000
        || client == null
        || !client.matches("[A-Za-z0-9._:-]{8,128}"))
      throw new ApiException(422, "Invalid group message");
    String text = body.strip();
    if (!limits.rateLimit("group-message", user, 30, 60))
      throw new ApiException(429, "RATE_LIMITED", "Too many messages; please wait");
    return guard.execute(
        user,
        () -> {
          var membership = require(user, book);
          if (membership.mutedUntil() != null && membership.mutedUntil().isAfter(Instant.now()))
            throw new ApiException(403, "MEMBER_MUTED", "Group member is muted");
          if (!language.allowed(text))
            throw new ApiException(
                422, "ENGLISH_ONLY", "Study group messages must be written in English");
          var existing = mapper.submission(book, user, client);
          if (existing != null) {
            if (!existing.getBody().equals(text))
              throw new ApiException(
                  409, "MESSAGE_CONFLICT", "Message identifier was used for different content");
            return map("created", false, "message", messageView(user, existing, new HashMap<>()));
          }
          var value = new StudyGroupMessage();
          value.setId(UUID.randomUUID().toString());
          value.setBookId(book);
          value.setSenderUserId(user);
          value.setBody(text);
          value.setClientMessageId(client);
          value.setCreatedAt(Instant.now());
          value.setUpdatedAt(value.getCreatedAt());
          mapper.insertMessage(value);
          // Fixed-width IDs retain the original client's lexical cursor ordering.
          value.setId(String.format(Locale.ROOT, "%024x", value.getSequence()));
          mapper.assignMessageId(value);
          mapper.event(book, value.getId());
          return map("created", true, "message", messageView(user, value, new HashMap<>()));
        });
  }

  private int rank(String role) {
    return switch (role) {
      case "admin" -> 2;
      case "moderator" -> 1;
      default -> 0;
    };
  }

  private void reason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new ApiException(422, "A moderation reason is required");
  }

  private void higher(StudyGroupMember actor, String targetUser, String targetRole) {
    if (actor.userId().equals(targetUser) || rank(actor.role()) <= rank(targetRole))
      throw new ApiException(
          403, "GOVERNANCE_FORBIDDEN", "Group governance action is not permitted");
  }

  public void moderate(
      String user,
      String book,
      String target,
      String role,
      Instant mute,
      boolean changeMute,
      String reason) {
    reason(reason);
    if (role == null && !changeMute
        || role != null && !Set.of("member", "moderator").contains(role))
      throw new ApiException(422, "Invalid moderation request");
    guard.execute(
        user,
        () -> {
          var actor = require(user, book);
          var subject = mapper.member(book, target);
          if (subject == null) throw new ApiException(404, "Member not found");
          if (role != null && (!actor.role().equals("admin") || user.equals(target)))
            throw new ApiException(
                403,
                "GOVERNANCE_FORBIDDEN",
                "Only an administrator may change another member's role");
          if (changeMute) higher(actor, target, subject.role());
          if (role != null) mapper.role(book, target, role);
          if (changeMute) mapper.mute(book, target, mute);
          audit(
              user,
              book,
              role != null ? "member.role_changed" : "member.mute_changed",
              "member",
              target,
              reason.strip());
          return null;
        });
  }

  public void removeMember(String user, String book, String target, String reason) {
    reason(reason);
    guard.execute(
        user,
        () -> {
          var actor = require(user, book);
          var subject = mapper.member(book, target);
          if (subject == null) throw new ApiException(404, "Member not found");
          if (!actor.role().equals("admin")
              || user.equals(target)
              || subject.role().equals("admin"))
            throw new ApiException(
                403,
                "GOVERNANCE_FORBIDDEN",
                "Only administrators may remove a non-administrator member");
          mapper.removeMember(book, target);
          mapper.memberEvents(book, target);
          audit(user, book, "member.removal_requested", "member", target, reason.strip());
          return null;
        });
  }

  public void deleteMessage(String user, String book, String id, String reason) {
    reason(reason);
    guard.execute(
        user,
        () -> {
          var actor = require(user, book);
          var message = mapper.message(book, id);
          if (message == null) throw new ApiException(404, "Message not found");
          if (message.getDeletedAt() != null) return null;
          if (!user.equals(message.getSenderUserId())) {
            var subject = mapper.member(book, message.getSenderUserId());
            higher(actor, message.getSenderUserId(), subject == null ? "member" : subject.role());
          }
          if (mapper.deleteMessage(book, id, user, reason.strip()) == 1) {
            audit(user, book, "message.deleted", "message", id, reason.strip());
            mapper.event(book, id);
          }
          return null;
        });
  }

  public void block(String user, String book, String target) {
    guard.execute(
        user,
        () -> {
          require(user, book);
          if (user.equals(target)) throw new ApiException(403, "Cannot block yourself");
          if (mapper.member(book, target) == null) throw new ApiException(404, "Member not found");
          mapper.block(user, target);
          return null;
        });
  }

  public void unblock(String user, String target) {
    guard.execute(
        user,
        () -> {
          mapper.unblock(user, target);
          return null;
        });
  }

  public Map<String, Object> report(
      String user,
      String book,
      String type,
      String message,
      String target,
      String reason,
      String details) {
    if (!Set.of("message", "user").contains(type)
        || !Set.of("harassment", "spam", "hate", "sexual", "violence", "other").contains(reason)
        || details != null && details.length() > 1000)
      throw new ApiException(422, "Invalid report");
    return guard.execute(
        user,
        () -> {
          require(user, book);
          String subject = target;
          if (type.equals("message")) {
            var source = mapper.message(book, message);
            if (source == null) throw new ApiException(404, "Message not found");
            subject = source.getSenderUserId();
          } else if (subject == null || mapper.member(book, subject) == null)
            throw new ApiException(404, "Member not found");
          if (user.equals(subject)) throw new ApiException(403, "Cannot report yourself");
          String id = UUID.randomUUID().toString();
          Instant now = Instant.now();
          mapper.report(
              new ContentReport(
                  id,
                  book,
                  user,
                  type,
                  type.equals("message") ? message : null,
                  subject,
                  reason,
                  Objects.toString(details, ""),
                  "open",
                  now,
                  now));
          audit(user, book, "report.created", "report", id, reason);
          return map("id", id, "status", "open");
        });
  }

  public Map<String, Object> events(String user, String book, String after) {
    return guard.execute(
        user,
        () -> {
          require(user, book);
          long latest = mapper.latestEvent(book);
          if (after == null)
            return map("events", List.of(), "cursor", Long.toString(latest), "hasMore", false);
          long cursor;
          try {
            if (!after.matches("[0-9]{1,19}")) throw new IllegalArgumentException();
            cursor = Long.parseLong(after);
            if (cursor > latest) throw new IllegalArgumentException();
          } catch (Exception error) {
            throw new ApiException(422, "Invalid event cursor");
          }
          var rows = mapper.events(book, cursor);
          var result = new ArrayList<Map<String, Object>>();
          var senders = new HashMap<String, Map<String, Object>>();
          for (var event : rows) {
            cursor = event.sequence();
            var message = mapper.message(book, event.messageId());
            if (message != null && !mapper.blocked(user, message.getSenderUserId()))
              result.add(
                  map(
                      "id",
                      Long.toString(event.sequence()),
                      "type",
                      "message",
                      "message",
                      messageView(user, message, senders)));
          }
          return map(
              "events",
              result,
              "cursor",
              Long.toString(cursor),
              "hasMore",
              rows.size() == 100 && cursor < latest);
        });
  }

  public void cleanupAccount(String user, String alias) {
    var owned = new HashSet<>(mapper.owned(user));
    var ids = new TreeSet<>(mapper.joined(user));
    ids.addAll(owned);
    for (String book : ids) {
      var source = mapper.lockBook(book);
      leaveLocked(user, book, owned.contains(book) || !open(source));
    }
    mapper.deleteBlocks(user);
    mapper.deleteReports(user);
    mapper.anonymizeMessages(user, alias);
    mapper.anonymizeDeleters(user, alias);
    mapper.anonymizeAudits(user, alias);
    mapper.anonymizeTargets(user, alias);
    mapper.anonymizeReports(user, alias);
  }
}
