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

	<form action="${pageContext.request.contextPath}/chat" method="post">
		<input type="text" name="question" placeholder="질문을 입력하세요"
			style="width: 300px;">
		<button type="submit">전송</button>
	</form>

	<hr>

	<p>
		<strong>사용자:</strong> ${question}
	</p>
	<p>
		<strong>챗봇:</strong> ${answer}
	</p>
</body>
</html>

