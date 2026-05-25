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
import java.util.ArrayList;
import java.util.List;

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
			if (employeeId != null) {
				employeeId = employeeId.trim().toUpperCase();
			}

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

	public Map<String, Object> searchByNameExact(String name) {
		try {
			if (name == null || name.trim().isEmpty()) {
				return null;
			}

			name = name.trim();

			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			Map<String, Object> term = new HashMap<>();
			term.put("이름.keyword", name);

			Map<String, Object> query = new HashMap<>();
			query.put("term", term);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", 1);

			String responseBody = sendPost(url, body);

			System.out.println("[OpenSearch] searchByNameExact name=" + name);
			System.out.println("[OpenSearch] response=" + responseBody);

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return null;
			}

			JsonNode source = hits.get(0).path("_source");

			return objectMapper.convertValue(source, Map.class);

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchByNameExact error");
			e.printStackTrace();
			return null;
		}
	}

	public List<Map<String, Object>> searchEmployeesByPosition(String position, int size) {
		List<Map<String, Object>> resultList = new ArrayList<>();

		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			Map<String, Object> term = new HashMap<>();
			term.put("직급.keyword", position);

			Map<String, Object> query = new HashMap<>();
			query.put("term", term);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", size);

			String responseBody = sendPost(url, body);

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return resultList;
			}

			for (JsonNode hit : hits) {
				JsonNode source = hit.path("_source");
				Map<String, Object> row = objectMapper.convertValue(source, Map.class);
				resultList.add(row);
			}

			return resultList;

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchEmployeesByPosition error");
			e.printStackTrace();
			return resultList;
		}
	}

	public Map<String, Object> searchTeamLeader(String department, String team) {
		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			Map<String, Object> mustDepartment = new HashMap<>();
			mustDepartment.put("term", Map.of("부서.keyword", department));

			Map<String, Object> mustTeam = new HashMap<>();
			mustTeam.put("term", Map.of("팀.keyword", team));

			Map<String, Object> mustRole = new HashMap<>();
			mustRole.put("term", Map.of("직책.keyword", "팀장"));

			List<Map<String, Object>> mustList = new ArrayList<>();
			mustList.add(mustDepartment);
			mustList.add(mustTeam);
			mustList.add(mustRole);

			Map<String, Object> bool = new HashMap<>();
			bool.put("must", mustList);

			Map<String, Object> query = new HashMap<>();
			query.put("bool", bool);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", 1);

			String responseBody = sendPost(url, body);
			JsonNode root = objectMapper.readTree(responseBody);

			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return null;
			}

			JsonNode source = hits.get(0).path("_source");

			return objectMapper.convertValue(source, Map.class);

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchTeamLeader error");
			e.printStackTrace();
			return null;
		}
	}

	public List<Map<String, Object>> searchManagers(String department, String team, int requesterPositionLevel) {
		List<Map<String, Object>> resultList = new ArrayList<>();

		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			List<Map<String, Object>> mustList = new ArrayList<>();

			Map<String, Object> departmentTerm = new HashMap<>();
			departmentTerm.put("부서.keyword", department);

			Map<String, Object> departmentQuery = new HashMap<>();
			departmentQuery.put("term", departmentTerm);
			mustList.add(departmentQuery);

			Map<String, Object> teamTerm = new HashMap<>();
			teamTerm.put("팀.keyword", team);

			Map<String, Object> teamQuery = new HashMap<>();
			teamQuery.put("term", teamTerm);
			mustList.add(teamQuery);

			List<Map<String, Object>> shouldList = new ArrayList<>();

			Map<String, Object> rangeCondition = new HashMap<>();
			rangeCondition.put("gt", requesterPositionLevel);

			Map<String, Object> positionLevelRange = new HashMap<>();
			positionLevelRange.put("직급레벨", rangeCondition);

			Map<String, Object> rangeQuery = new HashMap<>();
			rangeQuery.put("range", positionLevelRange);
			shouldList.add(rangeQuery);

			Map<String, Object> roleTerm = new HashMap<>();
			roleTerm.put("직책.keyword", "팀장");

			Map<String, Object> roleQuery = new HashMap<>();
			roleQuery.put("term", roleTerm);
			shouldList.add(roleQuery);

			Map<String, Object> bool = new HashMap<>();
			bool.put("must", mustList);
			bool.put("should", shouldList);
			bool.put("minimum_should_match", 1);

			Map<String, Object> query = new HashMap<>();
			query.put("bool", bool);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", 10);

			String responseBody = sendPost(url, body);
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return resultList;
			}

			for (JsonNode hit : hits) {
				JsonNode source = hit.path("_source");
				Map<String, Object> row = objectMapper.convertValue(source, Map.class);
				resultList.add(row);
			}

			return resultList;

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchManagers error");
			e.printStackTrace();
			return resultList;
		}
	}

	public List<Map<String, Object>> searchTeamMembersByQuestion(String question, int size) {
		List<Map<String, Object>> resultList = new ArrayList<>();

		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			List<Map<String, Object>> mustList = new ArrayList<>();

			Map<String, Object> matchText = new HashMap<>();
			matchText.put("embedding_text", question);

			Map<String, Object> matchQuery = new HashMap<>();
			matchQuery.put("match", matchText);
			mustList.add(matchQuery);

			Map<String, Object> roleTerm = new HashMap<>();
			roleTerm.put("직책.keyword", "팀원");

			Map<String, Object> roleQuery = new HashMap<>();
			roleQuery.put("term", roleTerm);
			mustList.add(roleQuery);

			Map<String, Object> bool = new HashMap<>();
			bool.put("must", mustList);

			Map<String, Object> query = new HashMap<>();
			query.put("bool", bool);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", size);

			String responseBody = sendPost(url, body);
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return resultList;
			}

			for (JsonNode hit : hits) {
				JsonNode source = hit.path("_source");
				Map<String, Object> row = objectMapper.convertValue(source, Map.class);
				resultList.add(row);
			}

			return resultList;

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchTeamMembersByQuestion error");
			e.printStackTrace();
			return resultList;
		}
	}

	public List<String> searchDepartmentList() {
		List<String> departments = new ArrayList<>();

		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			Map<String, Object> terms = new HashMap<>();
			terms.put("field", "부서.keyword");
			terms.put("size", 50);

			Map<String, Object> departmentAgg = new HashMap<>();
			departmentAgg.put("terms", terms);

			Map<String, Object> aggs = new HashMap<>();
			aggs.put("departments", departmentAgg);

			Map<String, Object> body = new HashMap<>();
			body.put("size", 0);
			body.put("aggs", aggs);

			String responseBody = sendPost(url, body);

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode buckets = root.path("aggregations").path("departments").path("buckets");

			if (!buckets.isArray() || buckets.size() == 0) {
				return departments;
			}

			for (JsonNode bucket : buckets) {
				departments.add(bucket.path("key").asText());
			}

			return departments;

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchDepartmentList error");
			e.printStackTrace();
			return departments;
		}
	}

	private List<Double> createQuestionEmbedding(String question) {
		try {
			String ollamaUrl = getConfig("OLLAMA_URL", "http://localhost:11434");
			String embeddingModel = getConfig("EMBEDDING_MODEL", "bge-m3:latest");

			String url = ollamaUrl + "/api/embed";

			Map<String, Object> body = new HashMap<>();
			body.put("model", embeddingModel);
			body.put("input", question);

			String jsonBody = objectMapper.writeValueAsString(body);

			URL requestUrl = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();

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

			if (statusCode < 200 || statusCode >= 300) {
				throw new RuntimeException("Ollama embedding API 오류: " + statusCode + " / " + responseBody);
			}

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode embeddings = root.path("embeddings");

			if (!embeddings.isArray() || embeddings.size() == 0) {
				throw new RuntimeException("Ollama embedding 응답에 embeddings 값이 없습니다.");
			}

			JsonNode firstEmbedding = embeddings.get(0);

			List<Double> vector = new ArrayList<>();

			for (JsonNode value : firstEmbedding) {
				vector.add(value.asDouble());
			}

			return vector;

		} catch (Exception e) {
			System.out.println("[OpenSearch] createQuestionEmbedding error");
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	public List<Map<String, Object>> vectorSearch(String indexName, String question, int size) {
		List<Map<String, Object>> resultList = new ArrayList<>();

		try {
			List<Double> queryVector = createQuestionEmbedding(question);

			if (queryVector == null || queryVector.isEmpty()) {
				return resultList;
			}

			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/" + indexName + "/_search";

			Map<String, Object> vectorBody = new HashMap<>();
			vectorBody.put("vector", queryVector);
			vectorBody.put("k", size);

			Map<String, Object> knnBody = new HashMap<>();
			knnBody.put("embedding_vector", vectorBody);

			Map<String, Object> query = new HashMap<>();
			query.put("knn", knnBody);

			Map<String, Object> source = new HashMap<>();

			List<String> excludes = new ArrayList<>();
			excludes.add("embedding_vector");

			source.put("excludes", excludes);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", size);
			body.put("_source", source);

			String responseBody = sendPost(url, body);

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return resultList;
			}

			for (JsonNode hit : hits) {
				JsonNode sourceNode = hit.path("_source");

				Map<String, Object> row = objectMapper.convertValue(sourceNode, Map.class);
				row.put("_score", hit.path("_score").asDouble());

				resultList.add(row);
			}

			return resultList;

		} catch (Exception e) {
			System.out.println("[OpenSearch] vectorSearch error");
			e.printStackTrace();
			return resultList;
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

	public List<Map<String, Object>> searchTeamMembers(String department, String team, int size) {
		List<Map<String, Object>> resultList = new ArrayList<>();

		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			List<Map<String, Object>> mustList = new ArrayList<>();

			Map<String, Object> departmentQuery = new HashMap<>();
			departmentQuery.put("term", Map.of("부서.keyword", department));
			mustList.add(departmentQuery);

			Map<String, Object> teamQuery = new HashMap<>();
			teamQuery.put("term", Map.of("팀.keyword", team));
			mustList.add(teamQuery);

			Map<String, Object> roleQuery = new HashMap<>();
			roleQuery.put("term", Map.of("직책.keyword", "팀원"));
			mustList.add(roleQuery);

			Map<String, Object> bool = new HashMap<>();
			bool.put("must", mustList);

			Map<String, Object> query = new HashMap<>();
			query.put("bool", bool);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", size);

			String responseBody = sendPost(url, body);
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return resultList;
			}

			for (JsonNode hit : hits) {
				JsonNode source = hit.path("_source");
				Map<String, Object> row = objectMapper.convertValue(source, Map.class);
				resultList.add(row);
			}

			return resultList;

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchTeamMembers error");
			e.printStackTrace();
			return resultList;
		}
	}

	public List<Map<String, Object>> searchTeamMembersByDepartment(String department, int size) {
		List<Map<String, Object>> resultList = new ArrayList<>();

		try {
			String url = getConfig("OPENSEARCH_URL", DEFAULT_OPENSEARCH_URL) + "/hr_basic_1/_search";

			List<Map<String, Object>> mustList = new ArrayList<>();

			Map<String, Object> departmentQuery = new HashMap<>();
			departmentQuery.put("term", Map.of("부서.keyword", department));
			mustList.add(departmentQuery);

			Map<String, Object> roleQuery = new HashMap<>();
			roleQuery.put("term", Map.of("직책.keyword", "팀원"));
			mustList.add(roleQuery);

			Map<String, Object> bool = new HashMap<>();
			bool.put("must", mustList);

			Map<String, Object> query = new HashMap<>();
			query.put("bool", bool);

			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("size", size);

			String responseBody = sendPost(url, body);
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode hits = root.path("hits").path("hits");

			if (!hits.isArray() || hits.size() == 0) {
				return resultList;
			}

			for (JsonNode hit : hits) {
				JsonNode source = hit.path("_source");
				Map<String, Object> row = objectMapper.convertValue(source, Map.class);
				resultList.add(row);
			}

			return resultList;

		} catch (Exception e) {
			System.out.println("[OpenSearch] searchTeamMembersByDepartment error");
			e.printStackTrace();
			return resultList;
		}
	}
}