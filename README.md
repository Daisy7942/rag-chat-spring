# LLM RAG 기반 인사 데이터 챗봇

## 1. 프로젝트 개요

본 프로젝트는 **Spring MVC 기반 채팅 화면**에서 사용자의 질문을 입력받고, **OpenSearch에 적재된 인사 데이터를 권한 레벨에 따라 검색**한 뒤, **FastAPI AI Engine과 Ollama 로컬 LLM**을 활용하여 답변을 생성하는 RAG 기반 챗봇 시스템이다.

기존 관계형 데이터 중심의 조회 구조를 벗어나, 인사 데이터를 OpenSearch 벡터 엔진에 적재하고 키워드 검색과 벡터 검색을 함께 활용할 수 있도록 구성하였다.

> 본 프로젝트에는 인사규정/규칙서 문서는 포함되어 있지 않으며, 기본인사정보·급여정보·역량성과 데이터를 기반으로 답변한다.

---

## 2. 주요 기능

- JSP 기반 채팅 화면 제공
- 사원번호 기반 사용자 식별
- 사용자 질문 유형 자동 분류
- 권한 레벨 기반 인사 데이터 조회 제한
- OpenSearch 기반 인사 데이터 검색
- Ollama `bge-m3:latest` 기반 임베딩 생성
- OpenSearch `knn_vector` 기반 벡터 검색
- FastAPI AI Engine을 통한 LLM 답변 생성
- Ollama `gemma4:e2b` 기반 최종 자연어 답변 생성
- 응답 결과에 출처 index/doc_id 포함

---

## 3. 기술 스택

| 구분 | 기술 |
|---|---|
| UI | JSP, HTML, CSS, JavaScript |
| Backend | Spring Framework / Spring MVC |
| AI Engine | FastAPI |
| Vector Store / Database | OpenSearch 3.3 |
| Dashboard | OpenSearch Dashboards 3.0 |
| LLM | Ollama `gemma4:e2b` |
| Embedding Model | Ollama `bge-m3:latest` |
| Data Processing | Python, pandas |
| Data Format | CSV, JSONL |
| API Test | Swagger / 브라우저 / 화면 테스트 |

---

## 4. 전체 시스템 구조

```text
사용자
  ↓
JSP 채팅 화면
  ↓
Spring MVC Backend
  - 사용자 요청 수신
  - 사원번호 기반 사용자 확인
  - 질문 유형 판단
  - 권한 레벨 확인
  - OpenSearch 검색
  - 검색 결과 context_data 생성
  ↓
FastAPI AI Engine
  - context_data와 question 수신
  - Ollama LLM 호출
  - 최종 답변 생성
  ↓
Spring MVC Backend
  ↓
JSP 채팅 화면 출력
```

---

## 5. RAG 처리 흐름

```text
1. 사용자가 사원번호와 질문 입력
2. Spring MVC Controller가 /chat/ajax 요청 수신
3. ChatService에서 요청자 사원번호 조회
4. 질문 유형 판단
5. 권한 레벨 확인
6. OpenSearch에서 관련 인사 데이터 검색
7. 검색 결과를 context_data로 구성
8. FastAPI /rag/answer API 호출
9. FastAPI가 Ollama gemma4:e2b 모델 호출
10. LLM 답변 생성
11. Spring이 JSON 응답 반환
12. JSP 화면에 챗봇 답변 출력
```

---

## 6. 데이터 구성

본 프로젝트는 다음 3개의 CSV 데이터를 기반으로 한다.

| 원천 데이터 | 주요 내용 |
|---|---|
| 기본인사정보 | 사원번호, 이름, 성별, 입사일, 부서, 팀, 직급, 직책, 이메일, 전화번호, 주소 등 |
| 급여정보 | 연봉, 잔업시간, 미사용휴가일수, 급여은행, 계좌번호 등 |
| 역량성과 | 성과점수, 인사고과, 자격증, TOEIC점수, 포상이력, 징계이력 등 |

CSV 데이터는 Python 스크립트를 통해 JSONL 형식으로 변환되며, 각 문서는 `doc_id`, `employee_id`, `embedding_text`를 포함한다.

---

## 7. OpenSearch 인덱스 설계

권한 레벨에 따라 데이터를 분리하여 OpenSearch 인덱스를 구성하였다.

| 인덱스명 | 권한 레벨 | 데이터 성격 |
|---|---:|---|
| `hr_basic_1` | Level 1 | 기본 공개 인사정보 |
| `hr_basic_2` | Level 2 | 생년월일, 학력, 전화번호 등 |
| `hr_basic_3` | Level 3 | 주민등록번호, 주소, 퇴직정보 등 |
| `hr_salary_2` | Level 2 | 잔업시간, 미사용휴가일수 |
| `hr_salary_3` | Level 3 | 연봉, 급여은행, 계좌번호, 4대보험 정보 |
| `hr_performance_2` | Level 2 | 성과점수, 인사고과, 자격증, TOEIC점수, 포상이력 |
| `hr_performance_3` | Level 3 | 징계이력, 징계사유, 자격증수당여부 |

---

## 8. 권한 처리 기준

사용자의 권한 레벨은 부서레벨과 직급레벨을 기준으로 산정한다.

```text
사용자 권한 레벨 = max(부서레벨, 직급레벨)
```

단, **인사팀은 전체 조회 가능 권한**을 가진다.

```text
if 부서 == "인사부":
    권한레벨 = 3
```

조회 허용 기준은 다음과 같다.

| 정보 유형 | 필요 권한 |
|---|---:|
| 기본 인사정보 | Level 1 |
| 전화번호 등 일부 민감 기본정보 | Level 2 |
| 주소, 주민등록번호 등 민감정보 | Level 3 |
| 성과 및 평가정보 | Level 2 |
| 급여 및 계좌정보 | Level 3 |

본인 정보는 권한 레벨과 관계없이 조회 가능하도록 설계하였다.

---

## 9. 모델 구성 및 선택 이유

### 9.1 LLM 모델: Ollama `gemma4:e2b`

`gemma4:e2b`는 검색된 인사 데이터를 기반으로 최종 답변을 생성하는 LLM 모델로 사용하였다.

선택 이유는 다음과 같다.

- 외부 GPT API 없이 로컬 환경에서 실행 가능
- API 비용 부담 감소
- 인사 데이터와 같은 민감 정보를 외부 서버로 전송하지 않음
- FastAPI에서 직접 호출하기 쉬움
- 검색된 context_data 기반 답변 생성에 적합

### 9.2 임베딩 모델: Ollama `bge-m3:latest`

`bge-m3:latest`는 사용자 질문과 인사 데이터를 벡터로 변환하기 위한 임베딩 모델로 사용하였다.

선택 이유는 다음과 같다.

- 한국어 문장 임베딩에 활용 가능
- OpenSearch `knn_vector` 검색과 연동 가능
- 로컬 Ollama 환경에서 실행 가능
- CSV/JSONL 데이터의 `embedding_text`를 벡터화하는 데 적합

### 9.3 두 모델을 분리한 이유

RAG 구조에서는 검색 단계와 답변 생성 단계의 역할이 다르다.

| 구분 | 사용 모델 | 역할 |
|---|---|---|
| 임베딩 모델 | `bge-m3:latest` | 질문과 문서를 벡터로 변환 |
| LLM 모델 | `gemma4:e2b` | 검색 결과를 바탕으로 답변 생성 |

따라서 본 프로젝트에서는 검색 정확도와 답변 생성 역할을 명확히 분리하기 위해 두 개의 모델을 사용하였다.

---

## 10. FastAPI AI Engine

FastAPI는 Spring에서 전달받은 검색 결과와 사용자 질문을 기반으로 Ollama LLM을 호출하는 AI Engine 역할을 수행한다.

### 10.1 Health Check API

| 항목 | 내용 |
|---|---|
| Method | GET |
| URL | `/health` |
| 설명 | FastAPI 서버 실행 상태 확인 |

응답 예시:

```json
{
  "success": true,
  "message": "FastAPI AI Engine is running"
}
```

### 10.2 RAG Answer API

| 항목 | 내용 |
|---|---|
| Method | POST |
| URL | `/rag/answer` |
| 설명 | Spring에서 전달한 context_data와 question을 기반으로 LLM 답변 생성 |
| 호출 주체 | Spring MVC Backend |
| 내부 호출 | Ollama `gemma4:e2b` |

요청 예시:

```json
{
  "question": "ㅇㅇㅇ님의 연봉 알려줘",
  "context_data": "이름: ㅇㅇㅇ 부서: 개발부 연봉: 45000000"
}
```

응답 예시:

```json
{
  "success": true,
  "answer": "ㅇㅇㅇ님의 연봉은 45,000,000원입니다."
}
```

---

## 11. Spring MVC API

### 11.1 채팅 화면

| 항목 | 내용 |
|---|---|
| Method | GET |
| URL | `/chat` |
| 설명 | JSP 기반 채팅 화면 반환 |

### 11.2 채팅 요청 API

| 항목 | 내용 |
|---|---|
| Method | POST |
| URL | `/chat/ajax` |
| Content-Type | `application/x-www-form-urlencoded; charset=UTF-8` |
| Response | `application/json; charset=UTF-8` |
| 설명 | 사용자 질문을 받아 인사 데이터 검색 및 답변 반환 |

요청 파라미터:

| 파라미터 | 설명 | 예시 |
|---|---|---|
| `employee_id` | 요청자 사원번호 | `EMP0001` |
| `question` | 사용자 질문 | `ㅇㅇㅇ님의 부서 알려줘` |

응답 예시:

```json
{
  "success": true,
  "question": "ㅇㅇㅇ님의 부서 알려줘",
  "answer": "ㅇㅇㅇ님은 개발부 소속입니다.",
  "permission": {
    "allowed": true,
    "permission_level": 1,
    "required_level": 1
  },
  "sources": [
    {
      "index": "hr_basic_1",
      "doc_id": "BAS1_00001"
    }
  ],
  "error": null
}
```

---

## 12. OpenSearch 검색 방식

본 프로젝트에서는 질문 유형에 따라 다양한 OpenSearch 검색 방식을 사용한다.

| 검색 방식 | 사용 목적 |
|---|---|
| `term` query | 사원번호, 이름, 직급 등 정확 일치 검색 |
| `bool` query | 부서, 팀, 직책 등 복합 조건 검색 |
| `range` query | 직급레벨 비교를 통한 상사 조회 |
| `terms aggregation` | 부서 목록 조회 |
| `knn` query | 임베딩 벡터 기반 의미 검색 |

---

## 13. 데이터 적재 흐름

```text
CSV 원천 데이터
  ↓
Python pandas로 CSV 읽기
  ↓
권한 레벨별 JSONL 문서 생성
  ↓
embedding_text 생성
  ↓
OpenSearch 인덱스 생성
  - Nori 분석기 설정
  - knn_vector 필드 설정
  ↓
Ollama bge-m3 임베딩 생성
  ↓
OpenSearch _bulk API로 데이터 적재
  ↓
검색 테스트 및 count 확인
```

---

## 14. 환경변수 설정

`.env` 파일 예시:

```env
OPENSEARCH_URL=https://localhost:9200
OPENSEARCH_USERNAME=admin
OPENSEARCH_PASSWORD=your_password

OLLAMA_URL=http://localhost:11434
EMBEDDING_MODEL=bge-m3:latest
```

> `.env` 파일은 보안상 Git에 업로드하지 않는다.

---

## 15. 실행 방법

### 15.1 Ollama 실행 및 모델 준비

```bash
ollama pull gemma4:e2b
ollama pull bge-m3:latest
ollama serve
```

### 15.2 OpenSearch 실행 확인

```bash
curl -k -u admin:비밀번호 https://localhost:9200
```
opensearch dashboards 접속
```bash
http://localhost:5601
```

### 15.3 FastAPI 실행

rag-chat-spring\ai-server 위치에서 
```bash
uvicorn main:app --reload --port 8000
```

정상 실행 확인:

```bash
http://localhost:8000/health
```

### 15.4 Spring MVC 실행

Tomcat 서버에 Spring MVC 프로젝트를 배포한 뒤 아래 주소로 접속한다.

```text
http://localhost:8181/rag-chat-spring/chat
```

---

## 16. 화면 구현

본 프로젝트는 JSP 기반 채팅 화면을 구현하였다.

화면 구성은 다음과 같다.

- 사원번호 입력 영역
- 질문 입력 영역
- 사용자 메시지 말풍선
- 챗봇 답변 말풍선
- 답변 생성 중 로딩 표시
- 입력값 검증 alert
- Ajax 기반 비동기 통신

### 화면 캡처 권장 항목

1. 채팅 화면 최초 진입 화면
  <img width="875" height="1083" alt="image" src="https://github.com/user-attachments/assets/c315a0d9-fcb7-4d0c-af68-431dcdcb7b9e" />

  
2. 정상 답변 예시 화면
  <img width="862" height="946" alt="image" src="https://github.com/user-attachments/assets/efd1ce3f-6f11-4524-9bd1-fa3321ae3cd3" />

  
3. 권한 제한 답변 예시 화면
  <img width="866" height="954" alt="image" src="https://github.com/user-attachments/assets/677bee1d-ec51-455e-8474-59cba702e475" />

  
4. FastAPI `/health` 정상 응답 화면
  <img width="1434" height="1064" alt="image" src="https://github.com/user-attachments/assets/1b5ca3b3-df27-4607-9988-b00e7891be70" />

  
  <img width="929" height="961" alt="image" src="https://github.com/user-attachments/assets/0d773287-5f81-4df2-9486-e9822fd603b5" />

  
5. OpenSearch 검색 결과 화면
  <img width="1169" height="1202" alt="2026-05-25 15 23 16" src="https://github.com/user-attachments/assets/3999328a-4b23-4b25-963a-9f47ef2ac57f" />

---

## 17. 프로젝트 특징

- 외부 GPT API 없이 로컬 기반 RAG 구조 구현
- OpenSearch를 벡터 저장소 및 검색 엔진으로 활용
- 권한 레벨별 인덱스 분리로 민감 데이터 조회 제한
- FastAPI를 AI Engine으로 분리하여 LLM 호출 책임 분리
- Spring MVC는 화면 및 비즈니스 흐름 제어 담당
- Ollama 기반 LLM/임베딩 모델을 분리 적용
- CSV 데이터를 JSONL로 변환 후 OpenSearch에 bulk 적재

---

## 18. 한계 및 개선 방향

| 구분 | 내용 |
|---|---|
| 현재 한계 | FastAPI는 LLM 답변 생성 역할 중심이며, OpenSearch 검색은 Spring에서 수행 |
| 개선 방향 1 | FastAPI에서 OpenSearch 검색까지 담당하도록 AI Engine 범위 확장 |
| 개선 방향 2 | 검색 결과 출처를 화면에 더 명확하게 표시 |
| 개선 방향 3 | 권한 정책을 별도 설정 파일 또는 DB로 분리 |
| 개선 방향 4 | Docker 기반 실행 환경 구성 |
| 개선 방향 5 | OpenSearch Dashboards를 활용한 데이터 검증 화면 추가 |

---

## 19. 최종 요약

본 프로젝트는 Spring MVC 기반 채팅 화면, FastAPI AI Engine, OpenSearch 3.3, OpenSearch Dashboards 3.0, Ollama 로컬 모델을 활용하여 구현한 **LLM RAG 기반 인사 데이터 챗봇**이다.

사용자는 사원번호와 질문을 입력하고, 시스템은 요청자의 권한을 판단한 뒤 OpenSearch에서 조회 가능한 인사 데이터를 검색한다. 검색된 데이터는 FastAPI AI Engine으로 전달되며, FastAPI는 Ollama `gemma4:e2b` 모델을 호출하여 최종 답변을 생성한다.

이를 통해 외부 GPT API 없이도 로컬 환경에서 권한 기반 인사 데이터 질의응답이 가능한 RAG 시스템을 구현하였다.
