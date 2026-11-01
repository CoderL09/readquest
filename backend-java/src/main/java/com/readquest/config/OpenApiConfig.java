package com.readquest.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI readQuestApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ReadQuest REST API")
                .version("1.0")
                .description(
                    "Page lists use items/page/pageSize/total/hasMore, pageSize 1..100 and offset <=10000. Exceptions are documented per resource. Errors preserve error.code/message and add requestId/details. Binary downloads, 204 responses and SSE streams retain their native formats. Refresh tokens use an HttpOnly cookie; access tokens use Bearer authentication. Simulated payment is available only in the local profile."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  OpenApiCustomizer commonErrors() {
    return api -> {
      var schemas =
          io.swagger.v3.core.converter.ModelConverters.getInstance()
              .read(com.readquest.vo.ApiError.class);
      schemas.forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
      api.getPaths()
          .forEach((path, item) ->
              item.readOperations()
                  .forEach(operation -> {
                    if (path.equals("/api/health")
                        || path.equals("/api/sources")
                        || path.startsWith("/api/catalog/")
                        || path.equals("/api/users")
                        || path.equals("/api/sessions")
                        || path.equals("/api/sessions/current/tokens"))
                      operation.setSecurity(java.util.List.of());
                    for (var error :
                        java.util.Map.of(
                                "401",
                                "Authentication required",
                                "403",
                                "Insufficient permission",
                                "404",
                                "Resource not found",
                                "409",
                                "Resource conflict",
                                "422",
                                "Invalid input or pagination",
                                "429",
                                "Rate or quota exceeded",
                                "500",
                                "Unexpected server error")
                            .entrySet()) {
                      if (!operation.getResponses().containsKey(error.getKey()))
                        operation
                            .getResponses()
                            .addApiResponse(
                                error.getKey(),
                                new ApiResponse()
                                    .description(error.getValue())
                                    .content(
                                        new Content()
                                            .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                    .schema(
                                                        new Schema<>()
                                                            .$ref(
                                                                "#/components/schemas/ApiError")))));
                    }
                  }));
    };
  }
}
