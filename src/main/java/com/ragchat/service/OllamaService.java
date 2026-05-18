package com.ragchat.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OllamaService {

	private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
	private static final String MODEL_NAME = "gemma4:e2b";

	private final ObjectMapper objectMapper = new ObjectMapper();

	public String generateAnswer(String contextData, String question) {

		try {
			String prompt = buildPrompt(contextData, question);

			Map<String, Object> requestBody = new HashMap<>();
			requestBody.put("model", MODEL_NAME);
			requestBody.put("prompt", prompt);
			requestBody.put("stream", false);

			String jsonBody = objectMapper.writeValueAsString(requestBody);

			URL url = new URL(OLLAMA_URL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			conn.setDoOutput(true);

			try (OutputStream os = conn.getOutputStream()) {
				byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			}

			int statusCode = conn.getResponseCode();

			InputStream responseStream;

			if (statusCode >= 200 && statusCode < 300) {
				responseStream = conn.getInputStream();
			} else {
				responseStream = conn.getErrorStream();
			}

			String responseBody = readStream(responseStream);

			if (statusCode != 200) {
				System.out.println("Ollama API Error: " + responseBody);
				return "LLM 호출 중 오류가 발생했습니다.";
			}

			JsonNode jsonNode = objectMapper.readTree(responseBody);
			String answer = jsonNode.path("response").asText();

			if (answer == null || answer.trim().isEmpty()) {
				return "LLM이 답변을 생성하지 못했습니다.";
			}

			return answer.trim();

		} catch (Exception e) {
			e.printStackTrace();
			return "LLM 답변 생성 중 오류가 발생했습니다.";
		}
	}

	private String buildPrompt(String contextData, String question) {

		return "" + "너는 인사 데이터 조회 챗봇이다.\n" + "답변 앞에 반드시 [OLLAMA]를 붙여라.\n" + "반드시 [조회된 데이터]에 있는 내용만 사용해서 답변해라.\n"
				+ "답변은 반드시 한국어로 작성해라.\n" + "답변은 한 문장으로 짧고 정확하게 작성해라.\n" + "\n" + "[조회된 데이터]\n" + contextData + "\n\n"
				+ "[사용자 질문]\n" + question + "\n\n" + "[답변]";
	}

	private String readStream(InputStream inputStream) throws Exception {

		StringBuilder sb = new StringBuilder();

		try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

			String line;

			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		}

		return sb.toString();
	}
}