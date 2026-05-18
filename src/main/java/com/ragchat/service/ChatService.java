package com.ragchat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    public Map<String, Object> generateAnswer(String employeeId, String question) {

        int userLevel = getUserPermissionLevel(employeeId);
        int requiredLevel = getRequiredPermissionLevel(question);

        boolean allowed = userLevel >= requiredLevel;

        Map<String, Object> permission = new HashMap<>();
        permission.put("allowed", allowed);
        permission.put("level", userLevel);
        permission.put("required_level", requiredLevel);

        List<Map<String, Object>> sources = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("question", question);
        result.put("permission", permission);
        result.put("sources", sources);

        if (!allowed) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", "ACCESS_DENIED");
            error.put("message", "현재 권한으로 조회할 수 없는 정보입니다.");

            result.put("success", false);
            result.put("answer", "요청하신 정보는 현재 권한으로 조회할 수 없습니다.");
            result.put("error", error);

            return result;
        }

        result.put("success", true);
        result.put("answer", "권한 확인 완료. 입력한 질문: " + question);
        result.put("error", null);

        return result;
    }

    private int getUserPermissionLevel(String employeeId) {
        if ("EMP0001".equals(employeeId)) {
            return 1;
        }

        if ("EMP0002".equals(employeeId)) {
            return 3;
        }

        return 0;
    }

    private int getRequiredPermissionLevel(String question) {
        if (question.contains("연봉") || question.contains("급여") || question.contains("평가")) {
            return 3;
        }

        return 1;
    }
}