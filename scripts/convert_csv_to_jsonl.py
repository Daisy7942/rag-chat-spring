import json
from pathlib import Path

import pandas as pd


BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "src" / "main" / "resources" / "data"
OUT_DIR = BASE_DIR / "jsonl"

OUT_DIR.mkdir(exist_ok=True)


def read_csv(filename):
    path = DATA_DIR / filename

    try:
        return pd.read_csv(path, encoding="utf-8-sig")
    except UnicodeDecodeError:
        return pd.read_csv(path, encoding="cp949")


def clean_value(value):
    if pd.isna(value):
        return ""
    return value


def write_jsonl(filename, rows):
    path = OUT_DIR / filename

    with open(path, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    print(f"created: {path} ({len(rows)} rows)")


def make_embedding_text(data):
    parts = []

    for key, value in data.items():
        if key in ["doc_id", "employee_id"]:
            continue

        if value == "":
            continue

        parts.append(f"{key}: {value}")

    return " / ".join(parts)


def build_doc(doc_id, employee_id, data):
    doc = {
        "doc_id": doc_id,
        "employee_id": employee_id,
        "embedding_text": make_embedding_text(data),
    }

    doc.update(data)
    return doc


def convert_basic(basic_df):
    hr_basic_1 = []
    hr_basic_2 = []
    hr_basic_3 = []

    for idx, row in basic_df.iterrows():
        seq = idx + 1
        employee_id = clean_value(row["사원번호"])

        basic_1_data = {
            "이름": clean_value(row["이름"]),
            "성별": clean_value(row["성별"]),
            "나이": clean_value(row["나이"]),
            "입사일": clean_value(row["입사일"]),
            "근속기간": clean_value(row["근속기간"]),
            "채용경로": clean_value(row["채용경로"]),
            "계약형태": clean_value(row["계약형태"]),
            "회사명": clean_value(row["회사명"]),
            "사업장위치": clean_value(row["사업장위치"]),
            "부서": clean_value(row["부서"]),
            "팀": clean_value(row["팀"]),
            "부서레벨": clean_value(row["부서레벨"]),
            "직급": clean_value(row["직급"]),
            "직책": clean_value(row["직책"]),
            "직급레벨": clean_value(row["직급레벨"]),
            "이메일": clean_value(row["이메일"]),
        }

        basic_2_data = {
            "생년월일": clean_value(row["생년월일"]),
            "병역": clean_value(row["병역"]),
            "학력": clean_value(row["학력"]),
            "출신대학": clean_value(row["출신대학"]),
            "학점": clean_value(row["학점"]),
            "이전직장명": clean_value(row["이전직장명"]),
            "이전최종직급": clean_value(row["이전최종직급"]),
            "이전담당업무": clean_value(row["이전담당업무"]),
            "전화번호": clean_value(row["전화번호"]),
        }

        basic_3_data = {
            "주민등록번호": clean_value(row["주민등록번호"]),
            "퇴직구분": clean_value(row["퇴직구분"]),
            "퇴직일자": clean_value(row["퇴직일자"]),
            "주소": clean_value(row["주소"]),
        }

        hr_basic_1.append(build_doc(f"BAS1_{seq:05d}", employee_id, basic_1_data))
        hr_basic_2.append(build_doc(f"BAS2_{seq:05d}", employee_id, basic_2_data))
        hr_basic_3.append(build_doc(f"BAS3_{seq:05d}", employee_id, basic_3_data))

    write_jsonl("hr_basic_1.jsonl", hr_basic_1)
    write_jsonl("hr_basic_2.jsonl", hr_basic_2)
    write_jsonl("hr_basic_3.jsonl", hr_basic_3)


def convert_salary(salary_df):
    hr_salary_2 = []
    hr_salary_3 = []

    for idx, row in salary_df.iterrows():
        seq = idx + 1
        employee_id = clean_value(row["사원번호"])

        salary_2_data = {
            "잔업시간": clean_value(row["잔업시간"]),
            "미사용휴가일수": clean_value(row["미사용휴가일수"]),
        }

        salary_3_data = {
            "연봉": clean_value(row["연봉"]),
            "급여은행": clean_value(row["급여은행"]),
            "계좌번호": clean_value(row["계좌번호"]),
            "4대보험가입여부": clean_value(row["4대보험가입여부"]),
        }

        hr_salary_2.append(build_doc(f"SAL2_{seq:05d}", employee_id, salary_2_data))
        hr_salary_3.append(build_doc(f"SAL3_{seq:05d}", employee_id, salary_3_data))

    write_jsonl("hr_salary_2.jsonl", hr_salary_2)
    write_jsonl("hr_salary_3.jsonl", hr_salary_3)


def convert_performance(performance_df):
    hr_performance_2 = []
    hr_performance_3 = []

    for idx, row in performance_df.iterrows():
        seq = idx + 1
        employee_id = clean_value(row["사원번호"])

        performance_2_data = {
            "성과점수": clean_value(row["성과점수"]),
            "인사고과_2020": clean_value(row["인사고과_2020"]),
            "인사고과_2021": clean_value(row["인사고과_2021"]),
            "인사고과_2022": clean_value(row["인사고과_2022"]),
            "인사고과_2023": clean_value(row["인사고과_2023"]),
            "인사고과_2024": clean_value(row["인사고과_2024"]),
            "자격증": clean_value(row["자격증"]),
            "TOEIC점수": clean_value(row["TOEIC점수"]),
            "포상이력": clean_value(row["포상이력"]),
        }

        performance_3_data = {
            "자격증수당여부": clean_value(row["자격증수당여부"]),
            "징계이력": clean_value(row["징계이력"]),
            "징계사유": clean_value(row["징계사유"]),
        }

        hr_performance_2.append(build_doc(f"PER2_{seq:05d}", employee_id, performance_2_data))
        hr_performance_3.append(build_doc(f"PER3_{seq:05d}", employee_id, performance_3_data))

    write_jsonl("hr_performance_2.jsonl", hr_performance_2)
    write_jsonl("hr_performance_3.jsonl", hr_performance_3)


def main():
    basic_df = read_csv("기본인사정보.csv")
    salary_df = read_csv("급여정보.csv")
    performance_df = read_csv("역량성과.csv")

    convert_basic(basic_df)
    convert_salary(salary_df)
    convert_performance(performance_df)

    print("JSONL 변환 완료")


if __name__ == "__main__":
    main()