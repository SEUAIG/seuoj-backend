from flask import Flask, jsonify, request, Response
from pathlib import Path
import threading
import time
import requests
import random
import json

app = Flask(__name__)
BASE_DIR = Path(__file__).resolve().parent.parent
TEST_FILE_PATH = BASE_DIR / "data" / "testcase.md"

# 模拟 GET /judge/problem/{pid}
@app.route('/judge/problem/<pid>', methods=['GET'])
def get_problem_content(pid):
    return jsonify({
        "code": 0,
        "message": "success",
        "data": {
            "pid": pid,
            "content": f"# 题目 {pid} 的详细描述\n\n这是一道经典算法题...\n\n## 输入格式\n第一行输入一个整数 n\n\n## 输出格式\n输出结果"
        }
    })


@app.route('/judge/problem/file/<pid>/<file_name>', methods=['GET'])
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


def process_judge(submission_id):
    """模拟评测过程并在完成后回调后端"""
    print(f"[Mock Judge] 开始评测任务: {submission_id}")
    
    # 模拟评测耗时 2-5 秒
    time.sleep(random.randint(2, 5))
    
    # 随机生成评测结果
    status_options = ["AC", "WA", "TLE", "MLE", "RE", "CE"]
    # 提高 AC 概率
    weights = [0.5, 0.2, 0.1, 0.05, 0.1, 0.05]
    status = random.choices(status_options, weights=weights, k=1)[0]
    
    result_detail = {
        "time": random.randint(10, 500), # ms
        "memory": random.randint(256, 10240), # KB
        "info": "All test cases passed" if status == "AC" else "Wrong Answer on test 3"
    }
    
    payload = {
        "status": status,
        "resultDetail": json.dumps(result_detail)
    }
    
    # 回调后端更新结果
    # 注意：后端地址假设为 localhost:8080
    callback_url = f"http://localhost:8080/judge/submission/{submission_id}"
    
    try:
        print(f"[Mock Judge] 发送回调: {callback_url}, payload={payload}")
        response = requests.put(callback_url, json=payload)
        print(f"[Mock Judge] 回调结果: {response.status_code}, {response.text}")
    except Exception as e:
        print(f"[Mock Judge] 回调失败: {e}")

# 模拟 POST /judge/submission - 接收评测请求
@app.route('/judge/submission', methods=['POST'])
def submit_for_judge():
    data = request.get_json()
    submission_id = data.get('submissionId', 'unknown')
    pid = data.get('pid', 'unknown')
    language = data.get('language', 'unknown')
    code_length = len(data.get('code', ''))
    
    print(f"[Mock Judge] 收到评测请求: submissionId={submission_id}, pid={pid}, language={language}, code_length={code_length}")
    
    # 启动后台线程异步处理评测
    if submission_id != 'unknown':
        threading.Thread(target=process_judge, args=(submission_id,)).start()
    
    return jsonify({
        "code": 0,
        "message": "评测请求已接收，正在排队处理"
    })

if __name__ == '__main__':
    print("Mock Judge Server running on port 8081...")
    print("Endpoints:")
    print("  GET  /judge/problem/{pid}")
    print("  POST /judge/submission")
    print("  GET /judge/problem/file/{pid}/{file_name}")
    app.run(port=8081)
