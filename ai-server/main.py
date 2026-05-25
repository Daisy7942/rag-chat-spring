from fastapi import FastAPI
from pydantic import BaseModel
import requests

app = FastAPI(title="RAG AI Engine")

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "gemma4:e2b"


class RagAnswerRequest(BaseModel):
    question: str
    context_data: str


class RagAnswerResponse(BaseModel):
    success: bool
    answer: str


@app.get("/health")
def health():
    return {
        "success": True,
        "message": "FastAPI AI Engine is running"
    }


@app.post("/rag/answer", response_model=RagAnswerResponse)
def generate_rag_answer(request: RagAnswerRequest):
    prompt = build_prompt(request.context_data, request.question)

    body = {
        "model": MODEL_NAME,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0,
            "top_p": 0.1
        }
    }

    try:
        response = requests.post(OLLAMA_URL, json=body, timeout=120)
        response.raise_for_status()

        data = response.json()
        answer = data.get("response", "").strip()

        if not answer:
            return {
                "success": False,
                "answer": "LLM이 답변을 생성하지 못했습니다."
            }

        return {
            "success": True,
            "answer": answer
        }

    except Exception as e:
        print("[FastAPI] Ollama 호출 오류:", e)
        return {
            "success": False,
            "answer": "FastAPI AI Engine에서 답변 생성 중 오류가 발생했습니다."
        }


def build_prompt(context_data: str, question: str) -> str:
    return f"""
너는 인사 데이터 조회 챗봇이다.
반드시 [조회된 데이터]에 있는 내용만 사용해서 답변해라.
제공되지 않은 정보는 추측하지 마라.
권한 판단은 이미 Spring 서버에서 끝났으므로 다시 판단하지 마라.
답변은 반드시 한국어로 작성해라.
답변은 짧고 정확하게 작성해라.
금액은 천 단위 쉼표와 원 단위를 포함해서 작성해라.

[조회된 데이터]
{context_data}

[사용자 질문]
{question}

[답변]
"""