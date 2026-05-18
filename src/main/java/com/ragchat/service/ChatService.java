package com.ragchat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	@Autowired
	private OllamaService ollamaService;

	@Autowired
	private OpenSearchService openSearchService;

	public Map<String, Object> generateAnswer(String employeeId, String question) {

		Map<String, Object> result = new HashMap<>();

		// 1. 요청자 기본정보 조회
		Map<String, Object> requester = openSearchService.searchByEmployeeId("hr_basic_1", employeeId);

		if (requester == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "존재하지 않는 사원번호입니다.");
			result.put("permission", createPermission(false, 0, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("EMPLOYEE_NOT_FOUND", "요청자 사원번호를 찾을 수 없습니다."));
			return result;
		}

		// 2. 질문 유형 판단
		String questionType = detectQuestionType(question);

		if ("unknown".equals(questionType)) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "질문을 이해하지 못했습니다. 기본정보, 연봉, 급여, 평가 중 하나를 질문해 주세요.");
			result.put("permission", createPermission(true, getPermissionLevel(requester), 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("INVALID_QUESTION", "지원하지 않는 질문 유형입니다."));
			return result;
		}

		// 3. 조회 대상 판단
		Map<String, Object> targetBasic = detectTargetEmployee(question, employeeId, requester);

		if (targetBasic == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "조회 대상 사원을 찾을 수 없습니다.");
			result.put("permission", createPermission(false, getPermissionLevel(requester), 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("TARGET_NOT_FOUND", "조회 대상 사원 정보를 찾을 수 없습니다."));
			return result;
		}

		String targetEmployeeId = String.valueOf(targetBasic.get("employee_id"));

		// 4. 권한 판단
		int requesterLevel = getPermissionLevel(requester);
		int requiredLevel = getRequiredLevel(questionType);

		boolean isSelf = employeeId.equals(targetEmployeeId);
		boolean allowed = isSelf || requesterLevel >= requiredLevel;

		if (!allowed) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "요청하신 정보는 현재 권한으로 조회할 수 없습니다.");
			result.put("permission", createPermission(false, requesterLevel, requiredLevel));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("ACCESS_DENIED", getAccessDeniedMessage(questionType, requiredLevel)));
			return result;
		}

		// 5. 질문 유형에 맞는 OpenSearch 인덱스 조회
		String indexName = getIndexName(questionType);
		Map<String, Object> targetData = openSearchService.searchByEmployeeId(indexName, targetEmployeeId);

		if (targetData == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "조회 가능한 데이터가 없습니다.");
			result.put("permission", createPermission(true, requesterLevel, requiredLevel));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("DATA_NOT_FOUND", "OpenSearch에서 대상 데이터를 찾을 수 없습니다."));
			return result;
		}

		// 6. Ollama에 넘길 데이터 구성
		String contextData = createContextData(targetBasic, targetData, questionType);

		// 7. Ollama 답변 생성
		String answer = ollamaService.generateAnswer(contextData, question);

		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);
		result.put("permission", createPermission(true, requesterLevel, requiredLevel));
		result.put("sources", createSources(indexName, targetData));
		result.put("error", null);

		return result;
	}

	private Map<String, Object> detectTargetEmployee(String question, String requesterEmployeeId,
			Map<String, Object> requester) {

		if (question == null || question.trim().isEmpty()) {
			return requester;
		}

		String q = question.replaceAll(" ", "");

		if (q.contains("내") || q.contains("나의") || q.contains("본인")) {
			return requester;
		}

		Pattern pattern = Pattern.compile("EMP\\d{4}");
		Matcher matcher = pattern.matcher(question);

		if (matcher.find()) {
			String targetEmployeeId = matcher.group();
			return openSearchService.searchByEmployeeId("hr_basic_1", targetEmployeeId);
		}

		Map<String, Object> searched = openSearchService.searchBasicByQuestion(question);

		if (searched != null) {
			return searched;
		}

		return requester;
	}

	private String detectQuestionType(String question) {
		if (question == null || question.trim().isEmpty()) {
			return "unknown";
		}

		String q = question.replaceAll(" ", "");

		if (q.contains("기본정보") || q.contains("소속") || q.contains("부서") || q.contains("팀") || q.contains("직급")) {
			return "basic";
		}

		if (q.contains("연봉") || q.contains("급여") || q.contains("월급")) {
			return "salary";
		}

		if (q.contains("평가") || q.contains("고과") || q.contains("성과")) {
			return "performance";
		}

		return "unknown";
	}

	private int getRequiredLevel(String questionType) {
		switch (questionType) {
		case "basic":
			return 1;
		case "performance":
			return 2;
		case "salary":
			return 3;
		default:
			return 1;
		}
	}

	private String getIndexName(String questionType) {
		switch (questionType) {
		case "basic":
			return "hr_basic_1";
		case "salary":
			return "hr_salary_3";
		case "performance":
			return "hr_performance_2";
		default:
			return "hr_basic_1";
		}
	}

	private int getPermissionLevel(Map<String, Object> employee) {
		int departmentLevel = toInt(employee.get("부서레벨"));
		int positionLevel = toInt(employee.get("직급레벨"));

		return Math.max(departmentLevel, positionLevel);
	}

	private int toInt(Object value) {
		if (value == null) {
			return 1;
		}

		if (value instanceof Number) {
			return ((Number) value).intValue();
		}

		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (Exception e) {
			return 1;
		}
	}

	private String createContextData(Map<String, Object> basicData, Map<String, Object> targetData,
			String questionType) {
		String employeeId = String.valueOf(basicData.get("employee_id"));
		String name = String.valueOf(basicData.get("이름"));
		String department = String.valueOf(basicData.get("부서"));
		String team = String.valueOf(basicData.get("팀"));
		String position = String.valueOf(basicData.get("직급"));

		StringBuilder sb = new StringBuilder();

		sb.append("사원번호: ").append(employeeId).append("\n");
		sb.append("이름: ").append(name).append("\n");

		if ("basic".equals(questionType)) {
			sb.append("부서: ").append(department).append("\n");
			sb.append("팀: ").append(team).append("\n");
			sb.append("직급: ").append(position).append("\n");
			sb.append("이메일: ").append(targetData.get("이메일")).append("\n");
		}

		if ("salary".equals(questionType)) {
			sb.append("연봉: ").append(targetData.get("연봉")).append("원\n");
			sb.append("급여은행: ").append(targetData.get("급여은행")).append("\n");
			sb.append("4대보험가입여부: ").append(targetData.get("4대보험가입여부")).append("\n");
		}

		if ("performance".equals(questionType)) {
			sb.append("성과점수: ").append(targetData.get("성과점수")).append("\n");
			sb.append("인사고과_2024: ").append(targetData.get("인사고과_2024")).append("\n");
			sb.append("TOEIC점수: ").append(targetData.get("TOEIC점수")).append("\n");
		}

		return sb.toString();
	}

	private Map<String, Object> createPermission(boolean allowed, int permissionLevel, int requiredLevel) {
		Map<String, Object> permission = new HashMap<>();
		permission.put("allowed", allowed);
		permission.put("permission_level", permissionLevel);
		permission.put("level", permissionLevel);
		permission.put("required_level", requiredLevel);
		return permission;
	}

	private List<Map<String, Object>> createSources(String indexName, Map<String, Object> targetData) {
		List<Map<String, Object>> sources = new ArrayList<>();

		Map<String, Object> source = new HashMap<>();
		source.put("index", indexName);
		source.put("doc_id", targetData.get("doc_id"));

		sources.add(source);
		return sources;
	}

	private Map<String, Object> createError(String code, String message) {
		Map<String, Object> error = new HashMap<>();
		error.put("code", code);
		error.put("message", message);
		return error;
	}

	private String getAccessDeniedMessage(String questionType, int requiredLevel) {
		switch (questionType) {
		case "salary":
			return "급여정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		case "performance":
			return "성과 및 평가정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		default:
			return "해당 정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		}
	}
}