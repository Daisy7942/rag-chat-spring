package com.ragchat.controller;

import java.util.Map;
import javax.servlet.http.HttpSession;

import com.ragchat.service.ChatService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/chat")
    public String chat() {
        return "chat";
    }

    @PostMapping(value = "/chat/ajax", produces = "application/json; charset=UTF-8")
    @ResponseBody
    public Map<String, Object> chatAjax(
            @RequestParam("employee_id") String employeeId,
            @RequestParam("question") String question,
            HttpSession session) {

        return chatService.generateAnswer(employeeId, question, session);
    }
}