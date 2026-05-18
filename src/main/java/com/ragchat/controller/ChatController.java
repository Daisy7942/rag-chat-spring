package com.ragchat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

	@GetMapping("/chat")
	public String chat() {
		return "chat";
	}

	@PostMapping("/chat")
	public String sendMessage(@RequestParam("question") String question, Model model) {
		String answer = "임시 응답입니다. 입력한 질문: " + question;

		model.addAttribute("question", question);
		model.addAttribute("answer", answer);

		return "chat";
	}
}