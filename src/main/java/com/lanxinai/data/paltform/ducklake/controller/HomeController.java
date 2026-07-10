package com.lanxinai.data.paltform.ducklake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // 打开应用根地址时直接进入可交互的 API 文档。
        return "redirect:/swagger-ui.html";
    }
}
