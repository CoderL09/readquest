package com.readquest.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class FrontendController {
  @GetMapping({
    "/",
    "/app",
    "/app/**",
    "/login",
    "/register",
    "/calibration",
    "/passport",
    "/review",
    "/library",
    "/discover",
    "/sources",
    "/vocabulary",
    "/notes",
    "/groups",
    "/groups/**",
    "/profile",
    "/billing",
    "/path",
    "/progress",
    "/journey",
    "/upload",
    "/read/**",
    "/reader/**"
  })
  public String frontend() {
    return "forward:/index.html";
  }
}
