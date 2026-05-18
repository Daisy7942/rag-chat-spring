import os
import urllib3

import requests
from dotenv import load_dotenv


urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

load_dotenv()

OPENSEARCH_URL = os.getenv("OPENSEARCH_URL", "https://localhost:9200")
OPENSEARCH_USERNAME = os.getenv("OPENSEARCH_USERNAME", "admin")
OPENSEARCH_PASSWORD = os.getenv("OPENSEARCH_PASSWORD")

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "bge-m3:latest")


INDEX_SECURITY_LEVELS = {
    "hr_basic_1": 1,
    "hr_basic_2": 2,
    "hr_basic_3": 3,
    "hr_performance_2": 2,
    "hr_performance_3": 3,
    "hr_salary_2": 2,
    "hr_salary_3": 3,
}


def get_embedding_dimension():
    url = f"{OLLAMA_URL}/api/embed"

    payload = {
        "model": EMBEDDING_MODEL,
        "input": "임베딩 차원 확인용 테스트 문장입니다."
    }

    response = requests.post(url, json=payload, timeout=60)
    response.raise_for_status()

    data = response.json()
    embeddings = data.get("embeddings")

    if not embeddings or not embeddings[0]:
        raise RuntimeError("Ollama embedding 응답에서 embeddings 값을 찾지 못했습니다.")

    dimension = len(embeddings[0])
    print(f"embedding model: {EMBEDDING_MODEL}")
    print(f"embedding dimension: {dimension}")

    return dimension


def create_index(index_name, security_level, dimension):
    url = f"{OPENSEARCH_URL}/{index_name}"

    exists_response = requests.head(
        url,
        auth=(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD),
        verify=False
    )

    if exists_response.status_code == 200:
        print(f"already exists: {index_name}")
        return

    body = {
        "settings": {
            "index": {
                "knn": True
            }
        },
        "mappings": {
            "_meta": {
                "security_level": security_level
            },
            "properties": {
                "doc_id": {
                    "type": "keyword"
                },
                "employee_id": {
                    "type": "keyword"
                },
                "embedding_text": {
                    "type": "text"
                },
                "embedding_vector": {
                    "type": "knn_vector",
                    "dimension": dimension
                }
            }
        }
    }

    response = requests.put(
        url,
        json=body,
        auth=(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD),
        verify=False
    )

    if response.status_code not in [200, 201]:
        print(f"failed: {index_name}")
        print(response.status_code)
        print(response.text)
        return

    print(f"created: {index_name} / security_level={security_level}")


def main():
    if not OPENSEARCH_PASSWORD:
        raise RuntimeError(".env에 OPENSEARCH_PASSWORD가 없습니다.")

    dimension = get_embedding_dimension()

    for index_name, security_level in INDEX_SECURITY_LEVELS.items():
        create_index(index_name, security_level, dimension)

    print("OpenSearch index creation completed.")


if __name__ == "__main__":
    main()