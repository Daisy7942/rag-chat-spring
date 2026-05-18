package com.ragchat.controller;

import java.util.HashMap;
import java.util.Map;

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
	public Map<String, Object> chatAjax(@RequestParam("question") String question) {

		String answer = chatService.generateAnswer(question);

		Map<String, Object> result = new HashMap<>();
		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);

		return result;
	}
}