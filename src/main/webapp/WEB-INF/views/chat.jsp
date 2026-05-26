<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>RAG 챗봇</title>

<style>
* {
	box-sizing: border-box;
}

body {
	margin: 0;
	font-family: Arial, "맑은 고딕", sans-serif;
	background: #f3f4f6;
	color: #111827;
}

.chat-page {
	width: 100%;
	min-height: 100vh;
	display: flex;
	justify-content: center;
	align-items: center;
	padding: 30px;
}

.chat-container {
	width: 760px;
	height: 820px;
	background: #ffffff;
	border-radius: 18px;
	box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
	display: flex;
	flex-direction: column;
	overflow: hidden;
}

.chat-header {
	padding: 22px 26px;
	border-bottom: 1px solid #e5e7eb;
	background: #ffffff;
}

.chat-header h2 {
	margin: 0;
	font-size: 22px;
	font-weight: 700;
}

.chat-header p {
	margin: 8px 0 0 0;
	color: #6b7280;
	font-size: 14px;
}

.chat-box {
	flex: 1;
	padding: 24px;
	overflow-y: auto;
	background: #fafafa;
}

.message-row {
	display: flex;
	margin-bottom: 16px;
}

.message-row.user-row {
	justify-content: flex-end;
}

.message-row.bot-row {
	justify-content: flex-start;
}

.message {
	max-width: 70%;
	padding: 13px 16px;
	border-radius: 16px;
	font-size: 15px;
	line-height: 1.6;
	white-space: pre-line;
	word-break: break-word;
}

.user-message {
	background: #2563eb;
	color: white;
	border-bottom-right-radius: 4px;
}

.bot-message {
	background: #ffffff;
	color: #111827;
	border: 1px solid #e5e7eb;
	border-bottom-left-radius: 4px;
}

.message-name {
	font-size: 12px;
	margin-bottom: 5px;
	opacity: 0.75;
}

.loading {
	color: #6b7280;
}

.chat-input-area {
	padding: 18px 22px;
	border-top: 1px solid #e5e7eb;
	background: #ffffff;
}

.employee-row {
	margin-bottom: 10px;
}

.employee-row input {
	width: 220px;
	padding: 10px 12px;
	border: 1px solid #d1d5db;
	border-radius: 10px;
	font-size: 14px;
	outline: none;
}

.input-row {
	display: flex;
	gap: 10px;
}

.input-row input {
	flex: 1;
	padding: 13px 14px;
	border: 1px solid #d1d5db;
	border-radius: 12px;
	font-size: 15px;
	outline: none;
}

input:focus {
	border-color: #2563eb;
	box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

button {
	width: 86px;
	border: none;
	border-radius: 12px;
	background: #2563eb;
	color: white;
	font-size: 15px;
	font-weight: 600;
	cursor: pointer;
}

button:hover {
	background: #1d4ed8;
}

button:disabled {
	background: #9ca3af;
	cursor: not-allowed;
}

.guide-text {
	margin-top: 8px;
	font-size: 12px;
	color: #9ca3af;
}
</style>
</head>

<body>

	<div class="chat-page">
		<div class="chat-container">

			<div class="chat-header">
				<h2>RAG 인사데이터 챗봇</h2>
				<p>OpenSearch 기반으로 인사 데이터를 검색해 답변합니다.</p>
			</div>

			<div id="chatBox" class="chat-box">
				<div class="message-row bot-row">
					<div class="message bot-message">
						<div class="message-name">챗봇</div>
						안녕하세요. 인사 데이터에 대해 궁금한 내용을 질문해 주세요.
					</div>
				</div>
			</div>

			<form id="chatForm" class="chat-input-area">
				<div class="employee-row">
					<input type="text" id="employeeId" name="employee_id"
						placeholder="사원번호 예: EMP0001">
				</div>

				<div class="input-row">
					<input type="text" id="question" name="question"
						placeholder="질문을 입력하세요. 예: 내 연봉 얼마야?">
					<button type="submit" id="sendButton">전송</button>
				</div>

				<div class="guide-text">
					Enter를 누르면 질문이 전송됩니다.
				</div>
			</form>

		</div>
	</div>

	<script>
		const contextPath = "${pageContext.request.contextPath}";

		const chatForm = document.getElementById("chatForm");
		const chatBox = document.getElementById("chatBox");
		const sendButton = document.getElementById("sendButton");

		chatForm.addEventListener("submit", function(event) {
			event.preventDefault();

			const questionInput = document.getElementById("question");
			const employeeIdInput = document.getElementById("employeeId");

			const question = questionInput.value.trim();
			const employeeId = employeeIdInput.value.trim();

			if (employeeId === "") {
				alert("사원번호를 입력하세요.");
				employeeIdInput.focus();
				return;
			}

			if (question === "") {
				alert("질문을 입력하세요.");
				questionInput.focus();
				return;
			}

			addUserMessage(question);

			const loadingId = "loading-" + Date.now();
			addBotLoadingMessage(loadingId);

			questionInput.value = "";
			sendButton.disabled = true;

			fetch(contextPath + "/chat/ajax", {
				method: "POST",
				headers: {
					"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
				},
				body: "employee_id=" + encodeURIComponent(employeeId)
					+ "&question=" + encodeURIComponent(question)
			})
			.then(function(response) {
				return response.json();
			})
			.then(function(data) {
				const loadingMessage = document.getElementById(loadingId);

				if (loadingMessage) {
					const answer = data.answer || "답변을 가져오지 못했습니다.";

					loadingMessage.classList.remove("loading");
					loadingMessage.innerHTML =
						"<div class='message-name'>챗봇</div>" + escapeHtml(answer);

					loadingMessage.removeAttribute("id");
				}

				sendButton.disabled = false;
				questionInput.focus();
				scrollToBottom();
			})
			.catch(function(error) {
				console.error("Error:", error);

				const loadingMessage = document.getElementById(loadingId);

				if (loadingMessage) {
					loadingMessage.classList.remove("loading");
					loadingMessage.innerHTML =
						"<div class='message-name'>챗봇</div>응답 처리 중 오류가 발생했습니다.";

					loadingMessage.removeAttribute("id");
				}

				sendButton.disabled = false;
				questionInput.focus();
				scrollToBottom();
			});
		});

		function addUserMessage(text) {
			const row = document.createElement("div");
			row.className = "message-row user-row";

			const message = document.createElement("div");
			message.className = "message user-message";

			message.innerHTML =
				"<div class='message-name'>사용자</div>" + escapeHtml(text);

			row.appendChild(message);
			chatBox.appendChild(row);
			scrollToBottom();
		}

		function addBotLoadingMessage(loadingId) {
			const row = document.createElement("div");
			row.className = "message-row bot-row";

			const message = document.createElement("div");
			message.className = "message bot-message loading";
			message.id = loadingId;

			message.innerHTML =
				"<div class='message-name'>챗봇</div>답변을 생성 중입니다...";

			row.appendChild(message);
			chatBox.appendChild(row);
			scrollToBottom();
		}

		function scrollToBottom() {
			chatBox.scrollTop = chatBox.scrollHeight;
		}

		function escapeHtml(text) {
			return String(text)
				.replace(/&/g, "&amp;")
				.replace(/</g, "&lt;")
				.replace(/>/g, "&gt;")
				.replace(/"/g, "&quot;")
				.replace(/'/g, "&#039;");
		}
	</script>

</body>
</html>