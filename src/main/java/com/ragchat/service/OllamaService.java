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

	private static final String FASTAPI_RAG_URL = "http://127.0.0.1:8000/rag/answer";

	private final ObjectMapper objectMapper = new ObjectMapper();

	public String generateAnswer(String contextData, String question) {

		try {
			Map<String, Object> requestBody = new HashMap<>();
			requestBody.put("context_data", contextData);
			requestBody.put("question", question);

			String jsonBody = objectMapper.writeValueAsString(requestBody);

			URL url = new URL(FASTAPI_RAG_URL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			conn.setDoOutput(true);
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(120000);

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

			if (statusCode < 200 || statusCode >= 300) {
				System.out.println("[FastAPI] API Error: " + responseBody);
				return "FastAPI AI Engine 호출 중 오류가 발생했습니다.";
			}

			JsonNode jsonNode = objectMapper.readTree(responseBody);

			boolean success = jsonNode.path("success").asBoolean(false);
			String answer = jsonNode.path("answer").asText();

			if (!success) {
				if (answer != null && !answer.trim().isEmpty()) {
					return answer.trim();
				}

				return "FastAPI AI Engine이 답변을 생성하지 못했습니다.";
			}

			if (answer == null || answer.trim().isEmpty()) {
				return "FastAPI AI Engine이 빈 답변을 반환했습니다.";
			}

			return answer.trim();

		} catch (Exception e) {
			e.printStackTrace();
			return "FastAPI AI Engine 연결 중 오류가 발생했습니다.";
		}
	}

	private String readStream(InputStream inputStream) throws Exception {

		if (inputStream == null) {
			return "";
		}

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