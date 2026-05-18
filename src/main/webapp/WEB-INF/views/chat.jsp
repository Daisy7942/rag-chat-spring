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
		<input type="text" id="question" name="question"
			placeholder="질문을 입력하세요" style="width: 400px;">
		<button type="submit">전송</button>
	</form>

	<script>
        const contextPath = "${pageContext.request.contextPath}";

        document.getElementById("chatForm").addEventListener("submit", function(event) {
            event.preventDefault();

            const questionInput = document.getElementById("question");
            const question = questionInput.value.trim();

            if (question === "") {
                alert("질문을 입력하세요.");
                return;
            }

            fetch(contextPath + "/chat/ajax", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
                },
                body: "question=" + encodeURIComponent(question)
            })
            .then(response => response.json())
            .then(data => {
                const chatBox = document.getElementById("chatBox");

                chatBox.innerHTML += "<p><strong>사용자:</strong> " + data.question + "</p>";
                chatBox.innerHTML += "<p><strong>챗봇:</strong> " + data.answer + "</p>";

                questionInput.value = "";
                chatBox.scrollTop = chatBox.scrollHeight;
            })
            .catch(error => {
                console.error("Error:", error);
                alert("응답 처리 중 오류가 발생했습니다.");
            });
        });
    </script>
</body>
</html>