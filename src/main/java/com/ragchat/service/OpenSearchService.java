package com.ragchat.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenSearchService {

	private static final String DEFAULT_OPENSEARCH_URL = "https://localhost:9200";
	private static final String DEFAULT_OPENSEARCH_USERNAME = "admin";

	private final ObjectMapper objectMapper = new ObjectMapper();

	public Map<String, Object> searchByEmployeeId(String indexName, String employeeId) {
		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/" + indexName + "/_search";

			Map<String, Object> term = new HashMap<>();
			term.put("employee_id", employeeId);

			Map<String, Object> query = new HashMap<>();
			query.put("term", term);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", 1);

			String responseBody = sendPost(url, body);

			System.out.println("[OpenSearch] searchByEmployeeId index=" + indexName + ", employeeId=" + employeeId);
			System.out.println("[OpenSearch] response=" + responseBody);

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return null;
			}

			JsonNode source = hits.get(0).path("_source");

			return objectMapper.convertValue(source, Map.class);

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchByEmployeeId error");
			e.printStackTrace();
			return null;
		}
	}

	public Map<String, Object> searchBasicByQuestion(String question) {
		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			Map<String, Object> match = new HashMap<>();
			match.put("embedding_text", question);

			Map<String, Object> query = new HashMap<>();
			query.put("match", match);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", 1);

			String responseBody = sendPost(url, body);

			System.out.println("[OpenSearch] searchBasicByQuestion question=" + question);
			System.out.println("[OpenSearch] response=" + responseBody);

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return null;
			}

			JsonNode source = hits.get(0).path("_source");

			return objectMapper.convertValue(source, Map.class);

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchBasicByQuestion error");
			e.printStackTrace();
			return null;
		}
	}

	private String sendPost(String urlString, Map<String, Object> body) throws Exception {
		trustAllCertificates();

		String username = getConfig("OPENSEARCH_USERNAME", DEFAULT_OPENSEARCH_USERNAME);
		String password = getConfig("OPENSEARCH_PASSWORD", "");

		if (password == null || password.trim().isEmpty()) {
			throw new RuntimeException("OPENSEARCH_PASSWORD가 설정되지 않았습니다.");
		}

		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		String auth = username + ":" + password;
		String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
		conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		conn.setDoOutput(true);

		String jsonBody = objectMapper.writeValueAsString(body);

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
			throw new RuntimeException("OpenSearch API 오류: " + statusCode + " / " + responseBody);
		}

		return responseBody;
	}

	private void trustAllCertificates() throws Exception {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustAllCerts, new SecureRandom());

		HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

		HostnameVerifier allHostsValid = (hostname, session) -> true;
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
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

	private String getConfig(String key, String defaultValue) {
		String value = System.getenv(key);

		if (value != null && !value.trim().isEmpty()) {
			return value;
		}

		value = readFromEnvFile(key);

		if (value != null && !value.trim().isEmpty()) {
			return value;
		}

		return defaultValue;
	}

	private String readFromEnvFile(String key) {
		try {
			File envFile = new File("D:/springdev/rag-chat-spring/.env");

			if (!envFile.exists()) {
				envFile = new File(".env");
			}

			if (!envFile.exists()) {
				return null;
			}

			for (String line : Files.readAllLines(envFile.toPath(), StandardCharsets.UTF_8)) {
				if (line.startsWith(key + "=")) {
					return line.substring((key + "=").length()).trim();
				}
			}

			return null;

		} catch (Exception e) {
			return null;
		}
	}
}