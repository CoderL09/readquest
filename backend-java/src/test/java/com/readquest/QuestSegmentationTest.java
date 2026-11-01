package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.mapper.IngestionMapper;
import com.readquest.pojo.*;
import com.readquest.service.*;
import com.readquest.service.impl.QuestSegmentationServiceImpl;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class QuestSegmentationTest {
  AiProvider provider = mock(AiProvider.class);
  MembershipService membership = mock(MembershipService.class);
  IngestionMapper imports = mock(IngestionMapper.class);
  AccountWriteGuard guard =
      new AccountWriteGuard() {
        public <T> T execute(String user, Supplier<T> action) {
          return action.get();
        }
      };
  QuestSegmentationService service =
      new QuestSegmentationServiceImpl(provider, membership, guard, imports, 24);

  QuestSegmentationTest() {
    Book book = new Book();
    book.setOwnerId("u");
    when(imports.lockBook("b")).thenReturn(book);
  }

  List<DocumentParser.Section> input(String source) {
    return List.of(
        new DocumentParser.Section("Original", source, "<p>Original HTML</p>", "chapter.xhtml"));
  }

  void pro() {
    UserEntitlement pro = new UserEntitlement();
    pro.setCode("pro");
    when(membership.active("u")).thenReturn(List.of(pro));
    when(provider.configured()).thenReturn(true);
    when(membership.rateLimit(anyString(), anyString(), anyInt(), anyLong())).thenReturn(true);
    when(membership.admit(anyString(), anyString(), anyString())).thenReturn(true);
  }

  @Test
  void localQuestPreservesEverySentenceAndContinuousKeepsHtml() {
    String source = "A long river flowed past the house. ".repeat(200);
    var result = service.prepare("u", "b", input(source), "quest", 200);
    assertTrue(result.size() > 2);
    assertEquals(
        source.strip().replaceAll("\\s+", " "),
        String.join(" ", result.stream().map(s -> s.section().text()).toList())
            .replaceAll("\\s+", " "));
    assertTrue(result.stream().allMatch(s -> s.section().text().endsWith(".")));
    assertEquals(
        "<p>Original HTML</p>",
        service.prepare("u", "b", input(source), "continuous", 500).getFirst().section().html());
    verify(provider, never()).json(anyString(), anyString());
  }

  @Test
  void acceptsOnlyCompleteProviderSegmentationAndRefundsAllFailedProviderWork() throws Exception {
    pro();
    String source = "The river flowed past the house. The children watched.";
    when(provider.json(anyString(), anyString()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"stages\":[{\"stageTitle\":\"By the River\",\"stageSummary\":\"The children"
                        + " watch a river.\",\"content\":\""
                        + source
                        + "\"}]}"));
    assertEquals(
        "By the River",
        service.prepare("u", "b", input(source), "quest", 500).getFirst().section().title());
    verify(membership, never()).providerFailed(anyString(), anyString());
    when(provider.json(anyString(), anyString()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"stages\":[{\"stageTitle\":\"Missing"
                        + " Text\",\"stageSummary\":\"Incorrect\",\"content\":\"The river flowed"
                        + " past the house.\"}]}"));
    var fallback = service.prepare("u", "b", input(source), "quest", 500);
    assertEquals(source, fallback.getFirst().section().text());
    assertEquals("Stage 1", fallback.getFirst().section().title());
    verify(membership).providerFailed(eq("u"), anyString());
  }

  @Test
  void deniedQuotaAndOversizedSentenceDoNotCallProvider() {
    pro();
    when(membership.admit(anyString(), anyString(), anyString())).thenReturn(false);
    service.prepare("u", "b", input("The river flowed."), "quest", 500);
    service.prepare("u", "b", input("a".repeat(16000)), "quest", 500);
    verify(provider, never()).json(anyString(), anyString());
  }
}
