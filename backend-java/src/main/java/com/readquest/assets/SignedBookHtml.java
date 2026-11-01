package com.readquest.assets;

import com.readquest.pojo.*;
import java.net.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class SignedBookHtml {
  private final BookAssetTokens tokens;

  public SignedBookHtml(BookAssetTokens tokens) {
    this.tokens = tokens;
  }

  public Book book(String user, Book book) {
    if (book.getCoverUrl() != null && book.getCoverUrl().startsWith("rqasset:"))
      book.setCoverUrl(tokens.url(user, book.getId(), book.getCoverUrl().substring(8)));
    return book;
  }

  public Chapter chapter(String user, Chapter chapter, List<Chapter> all) {
    if (chapter.getContentHtml() == null || chapter.getContentHtml().isBlank()) return chapter;
    var html = Jsoup.parseBodyFragment(chapter.getContentHtml());
    for (var image : html.select("img[src]")) {
      String source = image.attr("src");
      if (source.startsWith("rqasset:")) {
        String path = source.substring(8);
        if (EpubPaths.safe(path)) image.attr("src", tokens.url(user, chapter.getBookId(), path));
        else image.removeAttr("src");
      }
    }
    for (var link : html.select("a[data-epub-href]")) {
      String href = link.attr("data-epub-href");
      String path = href.split("#", 2)[0];
      String fragment = href.contains("#") ? href.substring(href.indexOf('#')) : "";
      all.stream()
          .filter(target -> path.equals(target.getSourceHref()))
          .findFirst()
          .ifPresent(target ->
              link.attr(
                  "href",
                  "/app/reader/" + target.getId() + "?bookId=" + chapter.getBookId() + fragment));
    }
    chapter.setContentHtml(html.body().html());
    return chapter;
  }
}
