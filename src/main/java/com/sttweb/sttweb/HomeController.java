package com.sttweb.sttweb;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

  @GetMapping("/")
  public String home() {
    return "index"; // index.jsp 또는 templates/index.html 을 반환
  }
}