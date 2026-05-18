package com.ragchat.service;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

	public String generateAnswer(String question) {
		return "임시 응답입니다. 입력한 질문: " + question;
	}
}