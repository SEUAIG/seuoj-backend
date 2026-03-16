import os
import threading
import time
import json
from pathlib import Path
from urllib import error, request as urllib_request

from flask import Flask, Response, jsonify, request

app = Flask(__name__)

BASE_DIR = Path(__file__).resolve().parent.parent
TEST_FILE_PATH = BASE_DIR / "data" / "testcase.md"
MOCK_JUDGE_PORT = int(os.getenv("MOCK_JUDGE_PORT", "9090"))
CALLBACK_BASE_URL = os.getenv("MOCK_JUDGE_CALLBACK_BASE_URL", "http://127.0.0.1:8082")
CALLBACK_DELAY_SECONDS = float(os.getenv("MOCK_JUDGE_CALLBACK_DELAY_SECONDS", "0.2"))


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"code": 0, "message": "success"})


@app.route("/judge/problem/<pid>", methods=["GET"])
def get_problem_content(pid):
    return jsonify({
        "code": 0,
        "message": "success",
        "data": {
            "pid": pid,
            "content": f"# 题目 {pid}\n\n这是一个用于集成测试的模拟题面。"
        }
    })


@app.route("/judge/problem/file/<pid>/<file_name>", methods=["GET"])
def get_problem_file(pid, file_name):
    if not TEST_FILE_PATH.exists():
        return jsonify({"code": 404, "message": f"test file not found: {TEST_FILE_PATH}"}), 404

    payload = TEST_FILE_PATH.read_bytes()
    return Response(
        payload,
        status=200,
        content_type="application/octet-stream",
        headers={
            "Content-Disposition": 'attachment; filename="description.md"',
            "Content-Length": str(len(payload))
        }
    )


def build_success_payload():
    return {
        "status": "Success",
        "resultDetail": [
            {
                "id": 1,
                "in": "1 2",
                "out": "3",
                "ans": "3",
                "sys": "ok",
                "time": 10,
                "mem": 1024,
                "type": "Accepted",
                "score": 100
            }
        ],
        "score": 100
    }


def process_judge(submission_id):
    print(f"[Mock Judge] 开始评测任务: {submission_id}")
    time.sleep(CALLBACK_DELAY_SECONDS)

    payload = build_success_payload()
    callback_url = f"{CALLBACK_BASE_URL}/judge/submission/{submission_id}"

    try:
        body = json.dumps(payload).encode("utf-8")
        req = urllib_request.Request(
            callback_url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="PUT"
        )
        print(f"[Mock Judge] 发送回调: {callback_url}, payload={payload}")
        with urllib_request.urlopen(req, timeout=10) as response:
            print(f"[Mock Judge] 回调结果: {response.status}, {response.read().decode('utf-8', errors='ignore')}")
    except error.URLError as exc:
        print(f"[Mock Judge] 回调失败: {exc}")
    except Exception as exc:
        print(f"[Mock Judge] 回调异常: {exc}")


@app.route("/judge/submission", methods=["POST"])
def submit_for_judge():
    data = request.get_json() or {}
    submission_id = data.get("submissionId", "unknown")
    pid = data.get("pid", "unknown")
    language = data.get("language", "unknown")
    code_length = len(data.get("code", ""))

    print(
        f"[Mock Judge] 收到评测请求: submissionId={submission_id}, pid={pid}, "
        f"language={language}, code_length={code_length}"
    )

    if submission_id != "unknown":
        threading.Thread(target=process_judge, args=(submission_id,), daemon=True).start()

    return jsonify({
        "code": 0,
        "message": "评测请求已接收，正在排队处理"
    })


if __name__ == "__main__":
    print(f"Mock Judge Server running on port {MOCK_JUDGE_PORT}...")
    print(f"Callback base url: {CALLBACK_BASE_URL}")
    app.run(host="127.0.0.1", port=MOCK_JUDGE_PORT)
