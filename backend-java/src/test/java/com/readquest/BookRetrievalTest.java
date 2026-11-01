package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.mapper.EmbeddingMapper;
import com.readquest.pojo.*;
import com.readquest.service.*;
import com.readquest.service.impl.BookRetrievalServiceImpl;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BookRetrievalTest {
  BookService books = mock(BookService.class);
  EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
  EmbeddingMapper cache = mock(EmbeddingMapper.class);
  AccountWriteGuard guard =
      new AccountWriteGuard() {
        public <T> T execute(String user, Supplier<T> action) {
          return action.get();
        }
      };
  BookRetrievalService service =
      new BookRetrievalServiceImpl(books, embeddings, cache, guard, new ObjectMapper());

  Chapter chapter(String id, String text) {
    var chapter = new Chapter();
    chapter.setId(id);
    chapter.setStageTitle(id);
    chapter.setContent(text);
    return chapter;
  }

  @Test
  void pastContextExcludesCurrentSentenceAndFutureChapters() {
    when(books.chapters("u", "b"))
        .thenReturn(
            List.of(
                chapter("one", "Earlier scene. Current sentence. Secret ending."),
                chapter("two", "A later spoiler.")));
    var result = service.retrieve("u", "b", "Current sentence.", null, true);
    assertEquals("Earlier scene.", result.passages().getFirst().text());
    assertEquals("lexical", result.method());
    assertThrows(
        ApiException.class, () -> service.retrieve("u", "b", "Invented sentence.", null, true));
    verify(embeddings, never()).embed(anyList());
  }

  @Test
  void semanticResultsUsePersistentModelBoundCacheAndReportHybridMethod() {
    when(books.chapters("u", "b"))
        .thenReturn(List.of(chapter("one", "A quiet river."), chapter("two", "A busy street.")));
    when(embeddings.configured()).thenReturn(true);
    when(embeddings.modelKey()).thenReturn("model-a");
    when(embeddings.embed(anyList()))
        .thenAnswer(call ->
            ((List<String>) call.getArgument(0))
                .stream()
                    .map(t -> t.contains("street") ? new double[] {0, 1} : new double[] {1, 0})
                    .toList());
    Map<String, String> stored = new HashMap<>();
    when(cache.find(eq("b"), anyString())).thenAnswer(call -> stored.get(call.getArgument(1)));
    doAnswer(
            call -> {
              BookEmbedding value = call.getArgument(0);
              stored.put(value.cacheKey(), value.vectorJson());
              return null;
            })
        .when(cache)
        .save(any());
    var first = service.retrieve("u", "b", "river", null, false);
    assertEquals("hybrid-vector", first.method());
    assertEquals(1, first.passages().getFirst().score(), 1e-10);
    assertEquals(2, stored.size());
    service.retrieve("u", "b", "river", null, false);
    verify(embeddings, times(3)).embed(anyList());
    when(embeddings.modelKey()).thenReturn("model-b");
    service.retrieve("u", "b", "river", null, false);
    assertEquals(4, stored.size());
  }

  @Test
  void inaccessibleBooksNeverReachEmbeddingProvider() {
    when(books.requireReadable("u", "b")).thenThrow(new ApiException(404, "Book not found"));
    assertThrows(ApiException.class, () -> service.retrieve("u", "b", "query", null, false));
    verifyNoInteractions(embeddings);
  }

  @Test
  void citationsRetainExactUtf16OffsetsAcrossChunksAndVectorRanking() {
    String text = "  " + "river 😀 ".repeat(450) + "  ";
    when(books.chapters("u", "b")).thenReturn(List.of(chapter("one", text)));
    when(embeddings.modelKey()).thenReturn("test");
    when(embeddings.embed(anyList()))
        .thenAnswer(call ->
            ((List<String>) call.getArgument(0)).stream().map(t -> new double[] {1, 0}).toList());
    for (boolean vector : List.of(false, true)) {
      when(embeddings.configured()).thenReturn(vector);
      var result = service.retrieve("u", "b", "river", null, false);
      assertEquals(3, result.passages().size());
      for (var source : result.passages()) {
        assertEquals(source.text(), text.substring(source.startOffset(), source.endOffset()));
        assertEquals(BookRetrievalServiceImpl.sourceHash(text), source.chapterHash());
        assertFalse(Character.isLowSurrogate(source.text().charAt(0)));
        assertFalse(Character.isHighSurrogate(source.text().charAt(source.text().length() - 1)));
      }
    }
  }

  @Test
  void overlappingPassagesKeepAnAnswerAcrossTheOldChunkBoundary() {
    String text =
        "An ordinary filler sentence. ".repeat(56)
            + "The rare amber necklace was hidden inside the old wooden clock. "
            + "More ordinary filler text. ".repeat(50);
    when(books.chapters("u", "b")).thenReturn(List.of(chapter("one", text)));
    var result =
        service.retrieve("u", "b", "Where was the rare amber necklace hidden?", null, false);
    assertTrue(
        result.passages().stream()
            .anyMatch(p ->
                p.text()
                    .contains("The rare amber necklace was hidden inside the old wooden clock.")));
    assertTrue(
        result.passages().stream()
            .allMatch(p -> text.substring(p.startOffset(), p.endOffset()).equals(p.text())));
  }
}
