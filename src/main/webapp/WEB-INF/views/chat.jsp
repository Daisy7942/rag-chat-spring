<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>RAG 챗봇</title>
</head>
<body>
	<h2>RAG 챗봇</h2>

	<div id="chatBox"
		style="border: 1px solid #ccc; width: 500px; height: 300px; padding: 10px; overflow-y: auto;">
		<p>챗봇에게 질문해보세요.</p>
	</div>

	<br>

	<form id="chatForm">
		<input type="text" id="employeeId" name="employee_id"
			placeholder="사원번호 입력 예: EMP0001" style="width: 400px;"> <br>
		<br> <input type="text" id="question" name="question"
			placeholder="질문을 입력하세요" style="width: 400px;">
		<button type="submit">전송</button>
	</form>

	<script>
    const contextPath = "${pageContext.request.contextPath}";

    document.getElementById("chatForm").addEventListener("submit", function(event) {
        event.preventDefault();

        const questionInput = document.getElementById("question");
        const question = questionInput.value.trim();

        const employeeIdInput = document.getElementById("employeeId");
        const employeeId = employeeIdInput.value.trim();

        if (employeeId === "") {
            alert("사원번호를 입력하세요.");
            return;
        }

        if (question === "") {
            alert("질문을 입력하세요.");
            return;
        }

        const chatBox = document.getElementById("chatBox");

        // 질문마다 고유한 로딩 ID 생성
        const loadingId = "loading-" + Date.now();

        chatBox.innerHTML += "<p><strong>사용자:</strong> " + question + "</p>";
        chatBox.innerHTML += "<p id='" + loadingId + "'><strong>챗봇:</strong> 답변을 생성 중입니다...</p>";
        chatBox.scrollTop = chatBox.scrollHeight;

        fetch(contextPath + "/chat/ajax", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
            },
            body: "employee_id=" + encodeURIComponent(employeeId)
                + "&question=" + encodeURIComponent(question)
        })
        .then(response => response.json())
        .then(data => {
            const loadingMessage = document.getElementById(loadingId);

            if (loadingMessage) {
                loadingMessage.innerHTML = "<strong>챗봇:</strong> " + data.answer;
                loadingMessage.removeAttribute("id");
            }

            questionInput.value = "";
            chatBox.scrollTop = chatBox.scrollHeight;
        })
        .catch(error => {
            console.error("Error:", error);

            const loadingMessage = document.getElementById(loadingId);

            if (loadingMessage) {
                loadingMessage.innerHTML = "<strong>챗봇:</strong> 응답 처리 중 오류가 발생했습니다.";
                loadingMessage.removeAttribute("id");
            }

            chatBox.scrollTop = chatBox.scrollHeight;
        });
    });
</script>
</body>
</html>