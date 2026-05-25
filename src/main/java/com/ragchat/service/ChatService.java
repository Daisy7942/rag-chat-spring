package com.ragchat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	@Autowired
	private OpenSearchService openSearchService;

	@Autowired
	private FieldMappingService fieldMappingService;

	@Autowired
	private OllamaService ollamaService;

	public Map<String, Object> generateAnswer(String employeeId, String question, HttpSession session) {

		if (employeeId != null) {
			employeeId = employeeId.trim().toUpperCase();
		}

		Map<String, Object> result = new HashMap<>();

		// 1. 요청자 기본정보 조회
		Map<String, Object> requester = openSearchService.searchByEmployeeId("hr_basic_1", employeeId);

		if (requester == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "입력한 사원번호가 존재하지 않습니다. 사원번호를 다시 확인해 주세요.");
			result.put("permission", createPermission(false, 0, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("REQUESTER_NOT_FOUND", "입력한 요청자 사원번호를 찾을 수 없습니다."));
			return result;
		}

		// 2. 사원번호가 바뀌면 이전 대화 대상 초기화
		if (session != null) {
			String sessionEmployeeId = (String) session.getAttribute("currentEmployeeId");

			if (sessionEmployeeId == null || !sessionEmployeeId.equals(employeeId)) {
				session.setAttribute("currentEmployeeId", employeeId);
				session.removeAttribute("lastTargetEmployeeId");
				session.removeAttribute("lastRagContext");
				session.removeAttribute("lastRagQuestion");
			}
		}

		// 3. 질문 유형 판단
		String questionType = detectQuestionType(question);

		// 4. 직급 기준 조회: 사장 누구야, 이사 누구야
		if ("position_lookup".equals(questionType)) {
			int requesterLevel = getPermissionLevel(requester);

			String position = detectPositionFromQuestion(question);

			if (position == null) {
				result.put("success", false);
				result.put("question", question);
				result.put("answer", "조회할 직급을 찾을 수 없습니다.");
				result.put("permission", createPermission(true, requesterLevel, 1));
				result.put("sources", new ArrayList<Map<String, Object>>());
				result.put("error", createError("POSITION_NOT_FOUND", "질문에서 직급을 찾을 수 없습니다."));
				return result;
			}

			List<Map<String, Object>> employees = openSearchService.searchEmployeesByPosition(position, 20);

			if (employees == null || employees.isEmpty()) {
				result.put("success", false);
				result.put("question", question);
				result.put("answer", position + " 직급에 해당하는 사원을 찾을 수 없습니다.");
				result.put("permission", createPermission(true, requesterLevel, 1));
				result.put("sources", new ArrayList<Map<String, Object>>());
				result.put("error", createError("DATA_NOT_FOUND", position + " 직급 사원을 찾을 수 없습니다."));
				return result;
			}

			String answer = createPositionLookupAnswer(position, employees);

			result.put("success", true);
			result.put("question", question);
			result.put("answer", answer);
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", createSourcesFromList("hr_basic_1", employees));
			result.put("error", null);

			return result;
		}

		// 5. RAG 후속 질문 처리: 그게 다야?, 더 알려줘
		if ("rag_followup".equals(questionType)) {
			return handleRagFollowup(employeeId, question, session, requester);
		}

		// 6. 상사 조회
		if ("manager".equals(questionType)) {
			int requesterLevel = getPermissionLevel(requester);
			int requesterPositionLevel = toInt(requester.get("직급레벨"));

			String requesterEmployeeId = String.valueOf(requester.get("employee_id"));
			String department = String.valueOf(requester.get("부서"));
			String team = String.valueOf(requester.get("팀"));

			List<Map<String, Object>> managers = openSearchService.searchManagers(department, team,
					requesterPositionLevel);

			managers = removeEmployeeFromList(managers, requesterEmployeeId);

			if (managers == null || managers.isEmpty()) {
				result.put("success", false);
				result.put("question", question);
				result.put("answer", "조회 가능한 상사 정보가 없습니다.");
				result.put("permission", createPermission(true, requesterLevel, 1));
				result.put("sources", new ArrayList<Map<String, Object>>());
				result.put("error", createError("DATA_NOT_FOUND", "같은 팀 내 상사 정보를 찾을 수 없습니다."));
				return result;
			}

			String answer = createManagersAnswer(managers);

			result.put("success", true);
			result.put("question", question);
			result.put("answer", answer);
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", createSourcesFromList("hr_basic_1", managers));
			result.put("error", null);

			return result;
		}

		// 7. 팀원 목록 조회
		if ("team_member".equals(questionType)) {
			int requesterLevel = getPermissionLevel(requester);

			String q = question == null ? "" : question.replaceAll(" ", "");

			String requesterEmployeeId = String.valueOf(requester.get("employee_id"));
			String department = String.valueOf(requester.get("부서"));
			String team = String.valueOf(requester.get("팀"));

			String requestedDepartment = detectDepartmentFromQuestion(q);

			List<Map<String, Object>> teamMembers;

			if (requestedDepartment != null) {
				teamMembers = openSearchService.searchTeamMembersByDepartment(requestedDepartment, 100);
			} else {
				teamMembers = openSearchService.searchTeamMembers(department, team, 100);
				teamMembers = removeEmployeeFromList(teamMembers, requesterEmployeeId);
			}

			if (teamMembers == null || teamMembers.isEmpty()) {
				result.put("success", false);
				result.put("question", question);
				result.put("answer", "조회 가능한 팀원 정보가 없습니다.");
				result.put("permission", createPermission(true, requesterLevel, 1));
				result.put("sources", new ArrayList<Map<String, Object>>());
				result.put("error", createError("DATA_NOT_FOUND", "조건에 맞는 팀원 정보를 찾을 수 없습니다."));
				return result;
			}

			String answer = createTeamMembersAnswer(teamMembers);

			result.put("success", true);
			result.put("question", question);
			result.put("answer", answer);
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", createSourcesFromList("hr_basic_1", teamMembers));
			result.put("error", null);

			return result;
		}

		// 8. 부서 목록 조회
		if ("department_list".equals(questionType)) {
			int requesterLevel = getPermissionLevel(requester);

			List<String> departments = openSearchService.searchDepartmentList();

			if (departments == null || departments.isEmpty()) {
				result.put("success", false);
				result.put("question", question);
				result.put("answer", "조회 가능한 부서 정보가 없습니다.");
				result.put("permission", createPermission(true, requesterLevel, 1));
				result.put("sources", new ArrayList<Map<String, Object>>());
				result.put("error", createError("DATA_NOT_FOUND", "부서 목록을 찾을 수 없습니다."));
				return result;
			}

			String answer = "등록된 부서는 " + String.join(", ", departments) + "입니다.";

			result.put("success", true);
			result.put("question", question);
			result.put("answer", answer);
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", null);

			return result;
		}

		// 9. 벡터 검색 + Ollama RAG
		if ("vector_rag".equals(questionType)) {
			int requesterLevel = getPermissionLevel(requester);

			List<Map<String, Object>> vectorResults = openSearchService.vectorSearch("hr_basic_1", question, 10);

			if (vectorResults == null || vectorResults.isEmpty()) {
				result.put("success", false);
				result.put("question", question);
				result.put("answer", "벡터 검색 결과가 없습니다.");
				result.put("permission", createPermission(true, requesterLevel, 1));
				result.put("sources", new ArrayList<Map<String, Object>>());
				result.put("error", createError("VECTOR_SEARCH_EMPTY", "OpenSearch 벡터 검색 결과가 없습니다."));
				return result;
			}

			String contextData = createRagContext(vectorResults);
			String answer = ollamaService.generateAnswer(contextData, question);

			if (session != null) {
				session.setAttribute("lastRagContext", contextData);
				session.setAttribute("lastRagQuestion", question);
			}

			result.put("success", true);
			result.put("question", question);
			result.put("answer", answer);
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", createSourcesFromList("hr_basic_1", vectorResults));
			result.put("error", null);

			return result;
		}

		// 10. 컬럼명 기반 동적 조회
		FieldMappingService.FieldMeta fieldMeta = fieldMappingService.findByQuestion(question);

		if (fieldMeta != null) {
			return handleDynamicFieldLookup(employeeId, question, session, requester, fieldMeta);
		}

		// 11. 그래도 못 알아들으면 unknown 처리
		if ("unknown".equals(questionType)) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "질문을 이해하지 못했습니다. 기본정보, 연봉, 급여, 평가, 주소, 상사, 팀원 중 하나를 질문해 주세요.");
			result.put("permission", createPermission(true, getPermissionLevel(requester), 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("INVALID_QUESTION", "지원하지 않는 질문 유형입니다."));
			return result;
		}

		// 12. 조회 대상 판단
		Map<String, Object> targetBasic = detectTargetEmployee(question, employeeId, requester, session);

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

		if (session != null) {
			session.setAttribute("lastTargetEmployeeId", targetEmployeeId);
		}

		// 13. 권한 판단
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

		// 14. 질문 유형에 맞는 OpenSearch 인덱스 조회
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

		// 15. 검증된 Java 답변 생성
		String answer = createVerifiedAnswer(targetBasic, targetData, questionType, question);

		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);
		result.put("permission", createPermission(true, requesterLevel, requiredLevel));
		result.put("sources", createSources(indexName, targetData));
		result.put("error", null);

		return result;
	}

	private Map<String, Object> handleRagFollowup(String employeeId, String question, HttpSession session,
			Map<String, Object> requester) {

		Map<String, Object> result = new HashMap<>();
		int requesterLevel = getPermissionLevel(requester);

		if (session == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "이전 검색 내용이 없어 이어서 답변할 수 없습니다.");
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("RAG_CONTEXT_NOT_FOUND", "세션에 이전 RAG context가 없습니다."));
			return result;
		}

		String lastRagContext = (String) session.getAttribute("lastRagContext");
		String lastRagQuestion = (String) session.getAttribute("lastRagQuestion");

		if (lastRagContext == null || lastRagContext.trim().isEmpty()) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "이전 검색 내용이 없어 이어서 답변할 수 없습니다.");
			result.put("permission", createPermission(true, requesterLevel, 1));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("RAG_CONTEXT_NOT_FOUND", "세션에 이전 RAG context가 없습니다."));
			return result;
		}

		String followupQuestion = "이전 질문: " + lastRagQuestion + "\n후속 질문: " + question;
		String answer = ollamaService.generateAnswer(lastRagContext, followupQuestion);

		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);
		result.put("permission", createPermission(true, requesterLevel, 1));
		result.put("sources", new ArrayList<Map<String, Object>>());
		result.put("error", null);

		return result;
	}

	private Map<String, Object> detectTargetEmployee(String question, String requesterEmployeeId,
			Map<String, Object> requester, HttpSession session) {

		if (question == null || question.trim().isEmpty()) {
			return requester;
		}

		String q = question.replaceAll(" ", "");

		// 1. 명확히 본인 질문
		if (q.contains("내") || q.contains("나의") || q.contains("본인") || q.contains("나는") || q.contains("난")
				|| q.equals("나")) {
			return requester;
		}

		// 2. "그 사람", "그분", "방금" 같은 후속 질문은 마지막 조회 대상 사용
		if (q.contains("그사람") || q.contains("그분") || q.contains("방금")) {
			Map<String, Object> lastTarget = getLastTargetEmployee(session);

			if (lastTarget != null) {
				return lastTarget;
			}

			return requester;
		}

		// 3. 질문에 요청자 본인 이름이 들어가면 본인
		String requesterName = String.valueOf(requester.get("이름"));

		if (requesterName != null && question.contains(requesterName)) {
			return requester;
		}

		// 4. 팀장 질문
		if (q.contains("팀장") || q.contains("팀장님")) {

			Map<String, Object> baseEmployee = requester;

			String targetName = extractKoreanName(question);

			if (targetName != null) {
				Map<String, Object> searchedEmployee = openSearchService.searchByNameExact(targetName);

				if (searchedEmployee != null) {
					baseEmployee = searchedEmployee;
				} else {
					return null;
				}
			}

			String department = String.valueOf(baseEmployee.get("부서"));
			String team = String.valueOf(baseEmployee.get("팀"));

			Map<String, Object> teamLeader = openSearchService.searchTeamLeader(department, team);

			if (teamLeader != null) {
				return teamLeader;
			}
		}

		// 5. EMP0001 같은 사원번호가 있으면 해당 사원
		Pattern pattern = Pattern.compile("EMP\\d{4}", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(question);

		if (matcher.find()) {
			String targetEmployeeId = matcher.group().toUpperCase();
			return openSearchService.searchByEmployeeId("hr_basic_1", targetEmployeeId);
		}

		// 6. 대상자가 없는 짧은 후속 질문이면 마지막 조회 대상 사용
		if (!hasExplicitTargetText(q)) {
			Map<String, Object> lastTarget = getLastTargetEmployee(session);

			if (lastTarget != null) {
				return lastTarget;
			}

			return requester;
		}

		// 7. 이름이 포함된 질문은 이름.keyword로 정확 검색
		String targetName = extractKoreanName(question);

		if (targetName != null) {
			Map<String, Object> searched = openSearchService.searchByNameExact(targetName);

			if (searched != null) {
				return searched;
			}

			return null;
		}

		return requester;
	}

	private String extractKoreanName(String question) {
		if (question == null) {
			return null;
		}

		String q = question.replaceAll(" ", "");

		String[] removeWords = { "어디살아", "어디살", "사는곳", "거주지", "집어디", "살아", "알려줘", "말해줘", "뭐야", "뭐임", "무엇", "어디", "전화번호",
				"휴대폰번호", "핸드폰번호", "연락처", "전화", "휴대폰", "핸드폰", "휴대전화", "이름", "성명", "사원번호", "사번", "번호", "부서", "팀", "직급",
				"직책", "기본정보", "소속", "주소", "연봉", "급여", "월급", "평가", "고과", "성과", "팀장님", "팀장", "상사", "윗사람", "관리자", "사장",
				"이사", "부장", "차장", "과장", "대리", "사원", "나는", "난", "나", "내", "본인", "무슨", "어떤", "누구", "누가", "꺼", "것", "다" };

		for (String word : removeWords) {
			q = q.replace(word, "");
		}

		q = q.replace("은", "");
		q = q.replace("는", "");
		q = q.replace("이", "");
		q = q.replace("가", "");
		q = q.replace("을", "");
		q = q.replace("를", "");
		q = q.replace("의", "");
		q = q.replace("?", "");

		if (q.trim().length() < 2) {
			return null;
		}

		Pattern pattern = Pattern.compile("[가-힣]{2,4}");
		Matcher matcher = pattern.matcher(q);

		if (matcher.find()) {
			return matcher.group();
		}

		return null;
	}

	private Map<String, Object> getLastTargetEmployee(HttpSession session) {
		if (session == null) {
			return null;
		}

		Object lastTargetEmployeeId = session.getAttribute("lastTargetEmployeeId");

		if (lastTargetEmployeeId == null) {
			return null;
		}

		return openSearchService.searchByEmployeeId("hr_basic_1", String.valueOf(lastTargetEmployeeId));
	}

	private boolean hasExplicitTargetText(String q) {
		if (q == null || q.trim().isEmpty()) {
			return false;
		}

		String cleaned = q;

		cleaned = cleaned.replace("알려줘", "");
		cleaned = cleaned.replace("말해줘", "");
		cleaned = cleaned.replace("뭐야", "");
		cleaned = cleaned.replace("뭐임", "");
		cleaned = cleaned.replace("무엇", "");
		cleaned = cleaned.replace("어디", "");

		cleaned = cleaned.replace("이름", "");
		cleaned = cleaned.replace("성명", "");
		cleaned = cleaned.replace("사원번호", "");
		cleaned = cleaned.replace("사번", "");

		cleaned = cleaned.replace("전화번호", "");
		cleaned = cleaned.replace("휴대폰번호", "");
		cleaned = cleaned.replace("핸드폰번호", "");
		cleaned = cleaned.replace("연락처", "");
		cleaned = cleaned.replace("전화", "");
		cleaned = cleaned.replace("휴대폰", "");
		cleaned = cleaned.replace("핸드폰", "");
		cleaned = cleaned.replace("휴대전화", "");

		cleaned = cleaned.replace("번호", "");
		cleaned = cleaned.replace("부서", "");
		cleaned = cleaned.replace("팀", "");
		cleaned = cleaned.replace("직급", "");
		cleaned = cleaned.replace("직책", "");
		cleaned = cleaned.replace("기본정보", "");
		cleaned = cleaned.replace("소속", "");
		cleaned = cleaned.replace("주소", "");
		cleaned = cleaned.replace("연봉", "");
		cleaned = cleaned.replace("급여", "");
		cleaned = cleaned.replace("월급", "");
		cleaned = cleaned.replace("평가", "");
		cleaned = cleaned.replace("고과", "");
		cleaned = cleaned.replace("성과", "");
		cleaned = cleaned.replace("상사", "");
		cleaned = cleaned.replace("윗사람", "");
		cleaned = cleaned.replace("관리자", "");

		cleaned = cleaned.replace("나는", "");
		cleaned = cleaned.replace("난", "");
		cleaned = cleaned.replace("나", "");
		cleaned = cleaned.replace("내", "");
		cleaned = cleaned.replace("무슨", "");
		cleaned = cleaned.replace("어떤", "");
		cleaned = cleaned.replace("누구", "");
		cleaned = cleaned.replace("누가", "");

		cleaned = cleaned.replace("은", "");
		cleaned = cleaned.replace("는", "");
		cleaned = cleaned.replace("이", "");
		cleaned = cleaned.replace("가", "");
		cleaned = cleaned.replace("을", "");
		cleaned = cleaned.replace("를", "");
		cleaned = cleaned.replace("의", "");
		cleaned = cleaned.replace("?", "");

		return cleaned.trim().length() >= 2;
	}

	private String detectQuestionType(String question) {
		if (question == null || question.trim().isEmpty()) {
			return "unknown";
		}

		String q = question.replaceAll(" ", "");

		if ((q.contains("사장") || q.contains("이사") || q.contains("부장") || q.contains("차장") || q.contains("과장")
				|| q.contains("대리"))
				&& (q.contains("누구") || q.contains("누가") || q.contains("알려줘") || q.contains("이름"))) {
			return "position_lookup";
		}

		if (q.contains("그게다") || q.contains("그게전부") || q.contains("더없") || q.contains("더있") || q.contains("다야")
				|| q.contains("전부야") || q.contains("더알려줘")) {
			return "rag_followup";
		}

		if (q.contains("상사") || q.contains("윗사람") || q.contains("관리자")) {
			return "manager";
		}

		if ((q.contains("부서") && (q.contains("종류") || q.contains("목록") || q.contains("전체") || q.contains("뭐뭐")))
				|| q.contains("부서리스트")) {
			return "department_list";
		}

		if (q.contains("팀원") || q.contains("팀원들") || q.contains("동료") || q.contains("구성원") || q.contains("직원목록")
				|| q.contains("명단") || q.contains("우리팀") || q.contains("같은팀")) {
			return "team_member";
		}

		if (q.contains("검색") || q.contains("찾아줘") || q.contains("찾아") || q.contains("요약") || q.contains("비슷")
				|| q.contains("높은") || q.contains("낮은") || q.contains("많은") || q.contains("적은") || q.contains("좋은")
				|| q.contains("우수")) {
			return "vector_rag";
		}

		if (q.contains("주소") || q.contains("어디살") || q.contains("사는곳") || q.contains("거주지") || q.contains("집어디")
				|| q.contains("주민등록번호") || q.contains("주민번호")) {
			return "basic_private";
		}

		if (q.contains("기본정보") || q.contains("소속") || q.contains("부서") || q.contains("팀") || q.contains("직급")
				|| q.contains("직책") || q.contains("이름") || q.contains("성명") || q.contains("사원번호") || q.contains("사번")
				|| q.contains("번호") || q.contains("누구") || q.contains("누가")) {
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

	private String detectPositionFromQuestion(String question) {
		if (question == null) {
			return null;
		}

		String q = question.replaceAll(" ", "");

		if (q.contains("사장")) {
			return "사장";
		}

		if (q.contains("이사")) {
			return "이사";
		}

		if (q.contains("부장")) {
			return "부장";
		}

		if (q.contains("차장")) {
			return "차장";
		}

		if (q.contains("과장")) {
			return "과장";
		}

		if (q.contains("대리")) {
			return "대리";
		}

		if (q.contains("사원")) {
			return "사원";
		}

		return null;
	}

	private String detectDepartmentFromQuestion(String q) {
		if (q == null) {
			return null;
		}

		if (q.contains("개발부") || q.contains("개발팀") || q.contains("개발")) {
			return "개발부";
		}

		if (q.contains("인사부") || q.contains("인사팀") || q.contains("인사")) {
			return "인사부";
		}

		if (q.contains("마케팅부") || q.contains("마케팅팀") || q.contains("마케팅")) {
			return "마케팅부";
		}

		if (q.contains("영업부") || q.contains("영업팀") || q.contains("영업")) {
			return "영업부";
		}

		return null;
	}

	private int getRequiredLevel(String questionType) {
		switch (questionType) {
		case "basic":
			return 1;
		case "team_member":
			return 1;
		case "manager":
			return 1;
		case "department_list":
			return 1;
		case "performance":
			return 2;
		case "salary":
			return 3;
		case "basic_private":
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
		case "basic_private":
			return "hr_basic_3";
		default:
			return "hr_basic_1";
		}
	}

	private int getPermissionLevel(Map<String, Object> employee) {
		int departmentLevel = toInt(employee.get("부서레벨"));
		int positionLevel = toInt(employee.get("직급레벨"));

		String department = String.valueOf(employee.get("부서"));

		if ("인사부".equals(department)) {
			departmentLevel = 3;
		}

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

	private Map<String, Object> handleDynamicFieldLookup(String employeeId, String question, HttpSession session,
			Map<String, Object> requester, FieldMappingService.FieldMeta fieldMeta) {

		Map<String, Object> result = new HashMap<>();

		Map<String, Object> targetBasic = detectTargetEmployee(question, employeeId, requester, session);

		if (targetBasic == null) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "조회 대상 사원을 찾을 수 없습니다.");
			result.put("permission",
					createPermission(false, getPermissionLevel(requester), fieldMeta.getRequiredLevel()));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("TARGET_NOT_FOUND", "조회 대상 사원 정보를 찾을 수 없습니다."));
			return result;
		}

		String targetEmployeeId = String.valueOf(targetBasic.get("employee_id"));

		if (session != null) {
			session.setAttribute("lastTargetEmployeeId", targetEmployeeId);
		}

		int requesterLevel = getPermissionLevel(requester);
		int requiredLevel = fieldMeta.getRequiredLevel();

		boolean isSelf = employeeId.equals(targetEmployeeId);
		boolean allowed = isSelf || requesterLevel >= requiredLevel;

		if (!allowed) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "요청하신 정보는 현재 권한으로 조회할 수 없습니다.");
			result.put("permission", createPermission(false, requesterLevel, requiredLevel));
			result.put("sources", new ArrayList<Map<String, Object>>());
			result.put("error", createError("ACCESS_DENIED",
					fieldMeta.getDisplayName() + " 정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다."));
			return result;
		}

		String indexName = fieldMeta.getIndexName();
		String fieldName = fieldMeta.getFieldName();

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

		Object value = targetData.get(fieldName);

		if (value == null || String.valueOf(value).trim().isEmpty() || "null".equals(String.valueOf(value))) {
			result.put("success", false);
			result.put("question", question);
			result.put("answer", "조회된 데이터에 해당 항목 값이 없습니다.");
			result.put("permission", createPermission(true, requesterLevel, requiredLevel));
			result.put("sources", createSources(indexName, targetData));
			result.put("error", createError("FIELD_VALUE_NOT_FOUND", fieldName + " 값을 찾을 수 없습니다."));
			return result;
		}

		String name = String.valueOf(targetBasic.get("이름"));
		String displayValue = formatFieldValue(fieldMeta, value);
		String answer = name + "님의 " + fieldMeta.getDisplayName() + " 정보는 " + displayValue + "입니다.";

		result.put("success", true);
		result.put("question", question);
		result.put("answer", answer);
		result.put("permission", createPermission(true, requesterLevel, requiredLevel));
		result.put("sources", createSources(indexName, targetData));
		result.put("error", null);

		return result;
	}

	private String formatFieldValue(FieldMappingService.FieldMeta fieldMeta, Object value) {
		if (value == null) {
			return "";
		}

		String fieldName = fieldMeta.getFieldName();
		String displayName = fieldMeta.getDisplayName();

		if (fieldName.contains("연봉") || fieldName.contains("급여") || fieldName.contains("월급")
				|| displayName.contains("연봉") || displayName.contains("급여") || displayName.contains("월급")) {

			try {
				String raw = String.valueOf(value).replace(",", "").replace("원", "").trim();

				long amount = Long.parseLong(raw);
				return String.format("%,d원", amount);
			} catch (Exception e) {
				return String.valueOf(value) + "원";
			}
		}

		return String.valueOf(value);
	}

	private String createVerifiedAnswer(Map<String, Object> basicData, Map<String, Object> targetData,
			String questionType, String question) {

		String name = String.valueOf(basicData.get("이름"));
		String department = String.valueOf(basicData.get("부서"));
		String team = String.valueOf(basicData.get("팀"));
		String position = String.valueOf(basicData.get("직급"));
		String role = String.valueOf(basicData.get("직책"));

		String q = question == null ? "" : question.replaceAll(" ", "");

		if ("basic".equals(questionType)) {
			if (q.contains("팀장") || q.contains("팀장님")) {
				return "팀장은 " + name + "님입니다.";
			}

			if (q.contains("이름") || q.contains("성명") || q.contains("누구") || q.contains("누가")) {
				return name + "님입니다.";
			}

			if (q.contains("직책")) {
				return name + "님의 직책은 " + role + "입니다.";
			}

			if (q.contains("직급")) {
				return name + "님의 직급은 " + position + "입니다.";
			}

			if (q.contains("사원번호") || q.contains("사번") || q.contains("번호")) {
				Object targetEmployeeId = basicData.get("employee_id");
				return name + "님의 사원번호는 " + targetEmployeeId + "입니다.";
			}

			if (q.contains("부서")) {
				return name + "님은 " + department + " 소속입니다.";
			}

			if (q.contains("팀")) {
				return name + "님은 " + team + " 소속입니다.";
			}

			return name + "님은 " + department + " " + team + " 소속 " + position + "이며, 직책은 " + role + "입니다.";
		}

		if ("basic_private".equals(questionType)) {
			if (q.contains("주소") || q.contains("어디살") || q.contains("사는곳") || q.contains("거주지") || q.contains("집어디")) {
				Object address = targetData.get("주소");
				return name + "님의 주소는 " + address + "입니다.";
			}

			if (q.contains("주민등록번호") || q.contains("주민번호")) {
				Object rrn = targetData.get("주민등록번호");
				return name + "님의 주민등록번호는 " + rrn + "입니다.";
			}

			return name + "님의 민감 기본정보는 " + targetData.get("embedding_text") + "입니다.";
		}

		if ("salary".equals(questionType)) {
			Object salaryValue = targetData.get("연봉");
			int salary = toInt(salaryValue);

			return name + "님의 연봉은 " + String.format("%,d", salary) + "원입니다.";
		}

		if ("performance".equals(questionType)) {
			Object score = targetData.get("성과점수");
			Object grade2024 = targetData.get("인사고과_2024");

			return name + "님의 성과점수는 " + score + "점이며, 2024년 인사고과는 " + grade2024 + "입니다.";
		}

		return "조회된 데이터로 답변을 생성할 수 없습니다.";
	}

	private String createPositionLookupAnswer(String position, List<Map<String, Object>> employees) {
		List<String> names = new ArrayList<>();

		for (Map<String, Object> employee : employees) {
			String name = String.valueOf(employee.get("이름"));
			String department = String.valueOf(employee.get("부서"));
			String team = String.valueOf(employee.get("팀"));

			names.add(name + "(" + department + " " + team + ")");
		}

		return position + " 직급 사원은 총 " + employees.size() + "명이며, " + String.join(", ", names) + "입니다.";
	}

	private String createManagersAnswer(List<Map<String, Object>> managers) {
		List<String> names = new ArrayList<>();

		for (Map<String, Object> manager : managers) {
			String name = String.valueOf(manager.get("이름"));
			String position = String.valueOf(manager.get("직급"));
			String role = String.valueOf(manager.get("직책"));

			names.add(name + "(" + position + ", " + role + ")");
		}

		return "조회된 상사는 " + String.join(", ", names) + "입니다.";
	}

	private String createTeamMembersAnswer(List<Map<String, Object>> teamMembers) {
		List<String> names = new ArrayList<>();

		for (Map<String, Object> member : teamMembers) {
			String name = String.valueOf(member.get("이름"));
			String department = String.valueOf(member.get("부서"));
			String team = String.valueOf(member.get("팀"));

			names.add(name + "(" + department + " " + team + ")");
		}

		return "조회된 팀원은 총 " + teamMembers.size() + "명이며, " + String.join(", ", names) + "입니다.";
	}

	private String createVectorSearchAnswer(List<Map<String, Object>> results) {
		List<String> items = new ArrayList<>();

		for (Map<String, Object> row : results) {
			String name = String.valueOf(row.get("이름"));
			String department = String.valueOf(row.get("부서"));
			String team = String.valueOf(row.get("팀"));
			String position = String.valueOf(row.get("직급"));
			String score = String.valueOf(row.get("_score"));

			items.add(name + "(" + department + " " + team + ", " + position + ", score=" + score + ")");
		}

		return "벡터 검색 결과는 " + String.join(", ", items) + "입니다.";
	}

	private String createRagContext(List<Map<String, Object>> results) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < results.size(); i++) {
			Map<String, Object> row = results.get(i);

			sb.append("[문서 ").append(i + 1).append("]\n");

			appendIfExists(sb, "사원번호", row.get("employee_id"));
			appendIfExists(sb, "이름", row.get("이름"));
			appendIfExists(sb, "부서", row.get("부서"));
			appendIfExists(sb, "팀", row.get("팀"));
			appendIfExists(sb, "직급", row.get("직급"));
			appendIfExists(sb, "직책", row.get("직책"));
			appendIfExists(sb, "검색내용", row.get("embedding_text"));
			appendIfExists(sb, "검색점수", row.get("_score"));

			sb.append("\n");
		}

		return sb.toString();
	}

	private void appendIfExists(StringBuilder sb, String label, Object value) {
		if (value != null && !String.valueOf(value).trim().isEmpty()) {
			sb.append(label).append(": ").append(value).append("\n");
		}
	}

	private List<Map<String, Object>> removeEmployeeFromList(List<Map<String, Object>> list, String employeeId) {
		List<Map<String, Object>> filtered = new ArrayList<>();

		if (list == null) {
			return filtered;
		}

		for (Map<String, Object> row : list) {
			String rowEmployeeId = String.valueOf(row.get("employee_id"));

			if (!employeeId.equals(rowEmployeeId)) {
				filtered.add(row);
			}
		}

		return filtered;
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

	private List<Map<String, Object>> createSourcesFromList(String indexName, List<Map<String, Object>> rows) {
		List<Map<String, Object>> sources = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			Map<String, Object> source = new HashMap<>();
			source.put("index", indexName);
			source.put("doc_id", row.get("doc_id"));
			sources.add(source);
		}

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
		case "basic_private":
			return "주소, 주민등록번호 등 민감 기본정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		default:
			return "해당 정보는 Level " + requiredLevel + " 이상만 조회할 수 있습니다.";
		}
	}
}