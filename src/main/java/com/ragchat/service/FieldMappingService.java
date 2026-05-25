package com.ragchat.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class FieldMappingService {

	public static class FieldMeta {
		private String fieldName;
		private String indexName;
		private int requiredLevel;
		private String displayName;

		public FieldMeta(String fieldName, String indexName, int requiredLevel, String displayName) {
			this.fieldName = fieldName;
			this.indexName = indexName;
			this.requiredLevel = requiredLevel;
			this.displayName = displayName;
		}

		public String getFieldName() {
			return fieldName;
		}

		public String getIndexName() {
			return indexName;
		}

		public int getRequiredLevel() {
			return requiredLevel;
		}

		public String getDisplayName() {
			return displayName;
		}
	}

	private final Map<String, FieldMeta> fieldMap = new HashMap<>();
	private final Map<String, String> aliasMap = new HashMap<>();

	public FieldMappingService() {
		// 기본 인사 정보
		register("이메일", "hr_basic_1", 1, "이메일", "메일", "email");
		register("입사일", "hr_basic_1", 1, "입사일", "입사 날짜", "입사날짜");

		// 기본 인사 민감 정보
		register("전화번호", "hr_basic_2", 2, "전화번호", "핸드폰", "휴대폰", "연락처", "전화", "전화 번호", "휴대폰번호", "핸드폰번호", "휴대전화");
		register("주소", "hr_basic_3", 3, "주소", "거주지");
		register("주민등록번호", "hr_basic_3", 3, "주민등록번호", "주민번호");

		// 급여 정보
		register("연봉", "hr_salary_3", 3, "연봉", "급여", "월급");
		register("계좌번호", "hr_salary_3", 3, "계좌번호", "급여계좌", "계좌");

		// 성과 정보
		register("성과점수", "hr_performance_2", 2, "성과점수", "성과 점수");
		register("인사고과_2024", "hr_performance_2", 2, "2024년 인사고과", "인사고과", "평가", "고과");
		register("TOEIC점수", "hr_performance_2", 2, "TOEIC점수", "토익", "토익점수", "TOEIC");
		register("징계이력", "hr_performance_3", 3, "징계이력", "징계");
	}

	private void register(String fieldName, String indexName, int requiredLevel, String displayName,
			String... aliases) {
		FieldMeta meta = new FieldMeta(fieldName, indexName, requiredLevel, displayName);

		fieldMap.put(fieldName, meta);
		aliasMap.put(normalize(fieldName), fieldName);
		aliasMap.put(normalize(displayName), fieldName);

		for (String alias : aliases) {
			aliasMap.put(normalize(alias), fieldName);
		}
	}

	public FieldMeta findByQuestion(String question) {
		if (question == null) {
			return null;
		}

		String q = normalize(question);

		for (String alias : aliasMap.keySet()) {
			if (q.contains(alias)) {
				String fieldName = aliasMap.get(alias);
				return fieldMap.get(fieldName);
			}
		}

		return null;
	}

	private String normalize(String text) {
		if (text == null) {
			return "";
		}

		return text.replaceAll(" ", "").toLowerCase();
	}
}