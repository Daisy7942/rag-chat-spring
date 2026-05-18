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

	private final Map<String, Map<String, Object>> employeeData = new HashMap<>();

	public ChatService() {
		// 요청자 EMP0001: 일반 사원 Level 1
		addEmployee("EMP0001", "지준서", "마케팅부", "브랜드팀", "사원", 1, 32000000, "B등급");

		// 요청자 EMP0002: 권한 높은 사용자 Level 3
		addEmployee("EMP0002", "김민수", "인사부", "인사관리팀", "부장", 3, 55000000, "A등급");
	}

	private void addEmployee(String employeeId, String name, String department, String team, String position,
			int permissionLevel, int salary, String evaluation) {
		Map<String, Object> employee = new HashMap<>();
		employee.put("employee_id", employeeId);
		employee.put("name", name);
		employee.put("department", department);
		employee.put("team", team);
		employee.put("position", position);
		employee.put("permission_level", permissionLevel);
		employee.put("salary", salary);
		employee.put("evaluation", evaluation);

		employeeData.put(employeeId, employee);
	}

	public Map<String, Object> generateAnswer(String employeeId, String question) {

		Map<String, Object> result = new HashMap<>();

		// 1. 요청자 확인
		Map<String, Object> requester = employeeData.get(employeeId);

		if (requester == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "존재하지 않는 사원번호입니다.");
			result.put("permission", createPermission(false, 0, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("EMPLOYEE_NOT_FOUND", "요청자 사원번호를 찾을 수 없습니다."));
			return result;
		}

		// 2. 질문 대상 사원번호 판단
		// 질문에 EMP0002 같은 사번이 있으면 그 사람 정보 조회
		// 없으면 본인 정보 조회
		String targetEmployeeId = detectTargetEmployeeId(question, employeeId);
		Map<String, Object> targetEmployee = employeeData.get(targetEmployeeId);

		if (targetEmployee == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "조회 대상 사원번호를 찾을 수 없습니다.");
			result.put("permission", createPermission(false, getPermissionLevel(requester), 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("TARGET_NOT_FOUND", "조회 대상 사원번호가 존재하지 않습니다."));
			return result;
		}

		// 3. 질문 유형 판단
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

		// 4. 권한 판단
		int requesterLevel = getPermissionLevel(requester);
		int requiredLevel = getRequiredLevel(questionType);

		boolean isSelf = employeeId.equals(targetEmployeeId);

		// 핵심 정책:
		// 본인 데이터면 허용
		// 타인 데이터면 permission_level >= requiredLevel 이어야 허용
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

		// 5. 권한 있으면 답변 생성
		String contextData = createContextData(targetEmployee, questionType);
		String answer = ollamaService.generateAnswer(contextData, question);

		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);
		result.put("permission", createPermission(true, requesterLevel, requiredLevel));
		result.put("sources", createSources(targetEmployeeId, questionType));
		result.put("error", null);

		return result;
	}

	private String detectTargetEmployeeId(String question, String defaultEmployeeId) {
		if (question == null) {
			return defaultEmployeeId;
		}

		// 1. 질문에 EMP0001 같은 사원번호가 있으면 우선 사용
		Pattern pattern = Pattern.compile("EMP\\d{4}");
		Matcher matcher = pattern.matcher(question);

		if (matcher.find()) {
			return matcher.group();
		}

		// 2. 질문에 이름이 포함되어 있으면 해당 사원번호 찾기
		for (Map.Entry<String, Map<String, Object>> entry : employeeData.entrySet()) {
			Map<String, Object> employee = entry.getValue();
			String name = (String) employee.get("name");

			if (name != null && question.contains(name)) {
				return (String) employee.get("employee_id");
			}
		}

		// 3. 사번도 이름도 없으면 본인 조회
		return defaultEmployeeId;
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

	private int getPermissionLevel(Map<String, Object> employee) {
		return (int) employee.get("permission_level");
	}

	private String createContextData(Map<String, Object> employee, String questionType) {
		String name = (String) employee.get("name");
		String department = (String) employee.get("department");
		String team = (String) employee.get("team");
		String position = (String) employee.get("position");
		int salary = (int) employee.get("salary");
		String evaluation = (String) employee.get("evaluation");

		switch (questionType) {
		case "basic":
			return "" + "이름: " + name + "\n" + "부서: " + department + "\n" + "팀: " + team + "\n" + "직급: " + position;

		case "salary":
			return "" + "이름: " + name + "\n" + "연봉: " + String.format("%,d", salary) + "원";

		case "performance":
			return "" + "이름: " + name + "\n" + "인사평가: " + evaluation;

		default:
			return "조회된 데이터가 없습니다.";
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

		case "performance":
			return name + "님의 인사평가는 " + evaluation + "입니다.";

		default:
			return "답변을 생성할 수 없습니다.";
		}
	}

	private Map<String, Object> createPermission(boolean allowed, int permissionLevel, int requiredLevel) {
		Map<String, Object> permission = new HashMap<>();
		permission.put("allowed", allowed);
		permission.put("permission_level", permissionLevel);
		permission.put("level", permissionLevel); // 기존 화면에서 level 쓰고 있어도 깨지지 않게 유지
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
			source.put("index", "hr_salary_3");
			source.put("doc_id", employeeId + "_salary");
			break;

		case "performance":
			source.put("index", "hr_performance_2");
			source.put("doc_id", employeeId + "_performance");
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
			return "급여정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";

		case "performance":
			return "성과 및 평가정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";

		default:
			return "해당 정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		}
	}
}