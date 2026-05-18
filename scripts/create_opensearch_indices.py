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


def delete_index_if_exists(index_name):
    url = f"{OPENSEARCH_URL}/{index_name}"

    exists_response = requests.head(
        url,
        auth=(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD),
        verify=False,
        timeout=30
    )

    if exists_response.status_code == 200:
        delete_response = requests.delete(
            url,
            auth=(OPENSEARCH_USERNAME, OPENSEARCH_PASSWORD),
            verify=False,
            timeout=60
        )

        if delete_response.status_code not in [200, 202]:
            print(f"delete failed: {index_name}")
            print(delete_response.status_code)
            print(delete_response.text)
            return

        print(f"deleted: {index_name}")
    else:
        print(f"not exists: {index_name}")


def create_index(index_name, security_level, dimension):
    url = f"{OPENSEARCH_URL}/{index_name}"

    body = {
        "settings": {
            "index": {
                "knn": True
            },
            "analysis": {
                "tokenizer": {
                    "korean_nori_tokenizer": {
                        "type": "nori_tokenizer",
                        "decompound_mode": "mixed"
                    }
                },
                "analyzer": {
                    "korean_nori": {
                        "type": "custom",
                        "tokenizer": "korean_nori_tokenizer",
                        "filter": [
                            "lowercase"
                        ]
                    }
                }
            }
        },
        "mappings": {
            "_meta": {
                "security_level": security_level
            },
            "dynamic_templates": [
                {
                    "strings_as_nori": {
                        "match_mapping_type": "string",
                        "mapping": {
                            "type": "text",
                            "analyzer": "korean_nori",
                            "search_analyzer": "korean_nori",
                            "fields": {
                                "keyword": {
                                    "type": "keyword",
                                    "ignore_above": 256
                                }
                            }
                        }
                    }
                }
            ],
            "properties": {
                "doc_id": {
                    "type": "keyword"
                },
                "employee_id": {
                    "type": "keyword"
                },
                "embedding_text": {
                    "type": "text",
                    "analyzer": "korean_nori",
                    "search_analyzer": "korean_nori"
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
        verify=False,
        timeout=60
    )

    if response.status_code not in [200, 201]:
        print(f"create failed: {index_name}")
        print(response.status_code)
        print(response.text)
        return

    print(f"created: {index_name} / security_level={security_level} / analyzer=korean_nori")


def main():
    if not OPENSEARCH_PASSWORD:
        raise RuntimeError(".env에 OPENSEARCH_PASSWORD가 없습니다.")

    dimension = get_embedding_dimension()

    for index_name, security_level in INDEX_SECURITY_LEVELS.items():
        delete_index_if_exists(index_name)
        create_index(index_name, security_level, dimension)

    print("OpenSearch index recreation with Nori completed.")


if __name__ == "__main__":
    main()