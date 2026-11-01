package com.readquest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Request-ID");
    String id =
        supplied != null && supplied.matches("[A-Za-z0-9-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", id);
    request.setAttribute("requestId", id);
    String previous = MDC.get("requestId");
    MDC.put("requestId", id);
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // Do not record query strings, raw IDs, bodies, cookies or authorization headers.
      Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (request.getRequestURI().startsWith("/api/"))
        LOG.info(
            "http method={} route={} status={} durationMs={} async={}",
            request.getMethod(),
            pattern == null ? "unmatched" : pattern,
            response.getStatus(),
            (System.nanoTime() - started) / 1_000_000,
            request.isAsyncStarted());
      if (previous == null) MDC.remove("requestId");
      else MDC.put("requestId", previous);
    }
  }
}
