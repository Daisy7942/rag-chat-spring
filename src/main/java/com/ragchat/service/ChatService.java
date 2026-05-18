package com.ragchat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

	private final Map<String, Map<String, Object>> employeeData = new HashMap<>();

	public ChatService() {
		// 테스트 목표에 맞춰 EMP0001, EMP0002 모두 지준서 데이터로 구성
		// 차이는 permission_level만 다르게 설정
		addEmployee("EMP0001", "지준서", "마케팅부", "브랜드팀", "사원", 32000000, "B등급", 1);

		addEmployee("EMP0002", "지준서", "마케팅부", "브랜드팀", "사원", 32000000, "B등급", 3);
	}

	private void addEmployee(String employeeId, String name, String department, String team, String position,
			int salary, String evaluation, int permissionLevel) {
		Map<String, Object> employee = new HashMap<>();
		employee.put("employee_id", employeeId);
		employee.put("name", name);
		employee.put("department", department);
		employee.put("team", team);
		employee.put("position", position);
		employee.put("salary", salary);
		employee.put("evaluation", evaluation);
		employee.put("permission_level", permissionLevel);

		employeeData.put(employeeId, employee);
	}

	public Map<String, Object> generateAnswer(String employeeId, String question) {

		Map<String, Object> result = new HashMap<>();

		Map<String, Object> employee = employeeData.get(employeeId);

		if (employee == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "존재하지 않는 사원번호입니다.");
			result.put("permission", createPermission(false, 0, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("EMPLOYEE_NOT_FOUND", "사원번호에 해당하는 사용자를 찾을 수 없습니다."));
			return result;
		}

		String questionType = detectQuestionType(question);
		int userLevel = (int) employee.get("permission_level");
		int requiredLevel = getRequiredLevel(questionType);

		if ("unknown".equals(questionType)) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "질문을 이해하지 못했습니다. 기본정보, 연봉, 급여, 평가 중 하나를 질문해 주세요.");
			result.put("permission", createPermission(true, userLevel, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("INVALID_QUESTION", "지원하지 않는 질문 유형입니다."));
			return result;
		}

		if (userLevel < requiredLevel) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "요청하신 정보는 현재 권한으로 조회할 수 없습니다.");
			result.put("permission", createPermission(false, userLevel, requiredLevel));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("ACCESS_DENIED", getAccessDeniedMessage(questionType, requiredLevel)));
			return result;
		}

		String answer = createAnswer(employee, questionType);

		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);
		result.put("permission", createPermission(true, userLevel, requiredLevel));
		result.put("sources", createSources(employeeId, questionType));
		result.put("error", null);

		return result;
	}

	private String detectQuestionType(String question) {
		if (question == null || question.trim().isEmpty()) {
			return "unknown";
		}

		String q = question.replaceAll(" ", "");

		if (q.contains("기본정보") || q.contains("소속") || q.contains("부서") || q.contains("팀")) {
			return "basic";
		}

		if (q.contains("연봉") || q.contains("급여") || q.contains("월급")) {
			return "salary";
		}

		if (q.contains("평가") || q.contains("등급") || q.contains("고과")) {
			return "evaluation";
		}

		return "unknown";
	}

	private int getRequiredLevel(String questionType) {
		switch (questionType) {
		case "basic":
			return 1;
		case "evaluation":
			return 2;
		case "salary":
			return 3;
		default:
			return 1;
		}
	}

	private String createAnswer(Map<String, Object> employee, String questionType) {
		String name = (String) employee.get("name");
		String department = (String) employee.get("department");
		String team = (String) employee.get("team");
		String position = (String) employee.get("position");
		int salary = (int) employee.get("salary");
		String evaluation = (String) employee.get("evaluation");

		switch (questionType) {
		case "basic":
			return name + "님은 " + department + " " + team + " 소속 " + position + "입니다.";

		case "salary":
			return name + "님의 연봉은 " + String.format("%,d", salary) + "원입니다.";

		case "evaluation":
			return name + "님의 인사평가는 " + evaluation + "입니다.";

		default:
			return "답변을 생성할 수 없습니다.";
		}
	}

	private Map<String, Object> createPermission(boolean allowed, int level, int requiredLevel) {
		Map<String, Object> permission = new HashMap<>();
		permission.put("allowed", allowed);
		permission.put("level", level);
		permission.put("required_level", requiredLevel);
		return permission;
	}

	private List<Map<String, Object>> createSources(String employeeId, String questionType) {
		List<Map<String, Object>> sources = new ArrayList<>();

		Map<String, Object> source = new HashMap<>();

		switch (questionType) {
		case "basic":
			source.put("index", "hr_basic_1");
			source.put("doc_id", employeeId + "_basic");
			break;

		case "salary":
			source.put("index", "hr_salary");
			source.put("doc_id", employeeId + "_salary");
			break;

		case "evaluation":
			source.put("index", "hr_evaluation");
			source.put("doc_id", employeeId + "_evaluation");
			break;

		default:
			source.put("index", "unknown");
			source.put("doc_id", "unknown");
			break;
		}

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
			return "연봉 및 급여정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";

		case "evaluation":
			return "평가정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";

		default:
			return "해당 정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		}
	}
}