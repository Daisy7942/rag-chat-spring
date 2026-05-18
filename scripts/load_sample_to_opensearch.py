import json
import os
from pathlib import Path

import requests
import urllib3
from dotenv import load_dotenv


urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent.parent
JSONL_DIR = BASE_DIR / "jsonl"

OPENSEARCH_URL = os.getenv("OPENSEARCH_URL", "https://localhost:9200")
OPENSEARCH_USERNAME = os.getenv("OPENSEARCH_USERNAME", "admin")
OPENSEARCH_PASSWORD = os.getenv("OPENSEARCH_PASSWORD")

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "bge-m3:latest")

SAMPLE_LIMIT = 10

INDEX_FILES = {
    "hr_basic_1": "hr_basic_1.jsonl",
    "hr_basic_2": "hr_basic_2.jsonl",
    "hr_basic_3": "hr_basic_3.jsonl",
    "hr_performance_2": "hr_performance_2.jsonl",
    "hr_performance_3": "hr_performance_3.jsonl",
    "hr_salary_2": "hr_salary_2.jsonl",
    "hr_salary_3": "hr_salary_3.jsonl",
}


def read_jsonl(file_name, limit):
    path = JSONL_DIR / file_name
    rows = []

    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if len(rows) >= limit:
                break

            rows.append(json.loads(line))

    return rows


def get_embeddings(texts):
    url = f"{OLLAMA_URL}/api/embed"

    payload = {
        "model": EMBEDDING_MODEL,
        "input": texts
    }

    response = requests.post(url, json=payload, timeout=180)
    response.raise_for_status()

    data = response.json()
    embeddings = data.get("embeddings")

    if not embeddings:
        raise RuntimeError("Ollama embedding 응답에 embeddings 값이 없습니다.")

    return embeddings


def bulk_insert(index_name, docs):
    if not docs:
        print(f"skip: {index_name}")
        return

    texts = [doc.get("embedding_text", "") for doc in docs]
    embeddings = get_embeddings(texts)

    bulk_lines = []

    for doc, embedding in zip(docs, embeddings):
        doc["embedding_vector"] = embedding

        action = {
            "index": {
                "_index": index_name,
                "_id": doc["doc_id"]
            }
        }

        bulk_lines.append(json.dumps(action, ensure_ascii=False))
        bulk_lines.append(json.dumps(doc, ensure_ascii=False))

    bulk_body = "\n".join(bulk_lines) + "\n"

    url = f"{OPENSEARCH_URL}/_bulk"

    response = requests.post(
        url,
        data=bulk_body.encode("utf-8"),
        headers={"Content-Type": "application/x-ndjson"},
        auth=(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD),
        verify=False,
        timeout=180
    )

    if response.status_code not in [200, 201]:
        print(f"bulk insert failed: {index_name}")
        print(response.status_code)
        print(response.text)
        return

    result = response.json()

    if result.get("errors"):
        print(f"bulk insert has errors: {index_name}")
        print(json.dumps(result, ensure_ascii=False, indent=2)[:3000])
        return

    print(f"inserted: {index_name} / {len(docs)} docs")


def search_employee(index_name, employee_id):
    url = f"{OPENSEARCH_URL}/{index_name}/_search"

    query = {
        "query": {
            "term": {
                "employee_id": employee_id
            }
        },
        "size": 3
    }

    response = requests.get(
        url,
        json=query,
        auth=(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD),
        verify=False,
        timeout=60
    )

    response.raise_for_status()

    result = response.json()
    hits = result["hits"]["hits"]

    print(f"\nsearch test: {index_name} / {employee_id}")
    print(f"hits: {len(hits)}")

    for hit in hits:
        source = hit["_source"]
        print({
            "index": hit["_index"],
            "doc_id": source.get("doc_id"),
            "employee_id": source.get("employee_id"),
            "embedding_text": source.get("embedding_text")
        })


def main():
    if not OPENSEARCH_PASSWORD:
        raise RuntimeError(".env에 OPENSEARCH_PASSWORD가 없습니다.")

    for index_name, file_name in INDEX_FILES.items():
        docs = read_jsonl(file_name, SAMPLE_LIMIT)
        bulk_insert(index_name, docs)

    print("\n샘플 적재 완료")

    search_employee("hr_basic_1", "EMP0001")
    search_employee("hr_salary_3", "EMP0001")
    search_employee("hr_performance_2", "EMP0001")


if __name__ == "__main__":
    main()