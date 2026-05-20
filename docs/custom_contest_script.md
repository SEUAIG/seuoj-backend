# 自定义赛制脚本说明（CUSTOM）

本文档说明后端当前支持的 `scoring_script` 写法。

## 1. 必须实现的函数

自定义脚本建议包含两个函数：

1. `getRequiredSubmissionFields()`
2. `computeStandings(input)`

其中 `computeStandings(input)` 是必须函数；`getRequiredSubmissionFields()` 用于声明需要的提交字段。

### 1.1 getRequiredSubmissionFields

```js
function getRequiredSubmissionFields() {
  return ["id", "language", "submit_time", "error_length"];
}
```

要求：
- 返回值必须是字符串数组。
- 返回的字段会与系统白名单取交集。
- 若函数不存在，或交集为空，后端会回退默认字段集。

声明含义（重要）：
- `getRequiredSubmissionFields()` 声明的是 **每条 submission 记录里需要注入的字段**。
- 这些字段不会出现在 `contest` 或 `problems`，只会出现在：
  - `input.users[<userId>].submissions[<pid>][i]`
- 也就是说，你声明了哪些字段，脚本里就应该从每条提交对象 `s.xxx` 读取这些字段。

例如声明：

```js
function getRequiredSubmissionFields() {
  return ["id", "verdict", "error_length", "submit_time"];
}
```

则 `computeStandings` 中每条提交对象形如：

```js
const s = user.submissions[pid][i];
// 可访问：
// s.id, s.verdict, s.error_length, s.submit_time
```

可选字段如下：

| 字段名 | 类型 | 含义 | 缺失默认值 |
|---|---|---|---|
| `id` | number | 提交记录主键 ID | `0` |
| `language` | string | 提交使用的编程语言 | `""` |
| `score` | number | 该次提交的评测得分 | `0` |
| `verdict` | string | 判题结果（如 `Accepted`/`WA`/`CE`） | `""` |
| `submit_time` | string | 提交时间（ISO-8601 字符串） | `""` |
| `error_detail` | string | 编译/运行错误详情文本 | `""` |
| `error_length` | number | `error_detail` 的字符长度 | `0` |

默认字段集（未声明或声明无效时回退）：
- `id`, `language`, `score`, `verdict`, `submit_time`, `error_detail`, `error_length`


### 1.2 computeStandings

```js
function computeStandings(input) {
  // 返回排行榜数组
  return [];
}
```

要求：
- 必须返回数组。
- 数组每项代表一名选手。

---

## 2. input 数据结构

```json
{
  "contest": {
    "start_time": "2026-05-20T10:00",
    "end_time": "2026-05-20T12:00",
    "config": "{...}"
  },
  "problems": [
    {"pid": "A", "sort_order": 1}
  ],
  "users": {
    "10001": {
      "username": "alice",
      "nickname": "Alice",
      "submissions": {
        "p01": [
          {
            "id": 123,
            "language": "cpp17",
            "score": 100,
            "verdict": "Accepted",
            "submit_time": "2026-05-20T10:30",
            "error_detail": "...",
            "error_length": 42
          }
        ]
      }
    }
  }
}
```

说明：
- `users` 的 key 是用户 ID（字符串）。
- `submissions` 按 `pid` 分组，值为该题提交列表。

---

## 3. computeStandings 返回结构

`computeStandings(input)` 必须返回 **数组**，数组每一项代表一名选手的排行记录。

### 3.1 顶层记录字段

| 字段名 | 类型 | 是否必须 | 说明 |
|---|---|---|---|
| `user_id` | number | 否 | 选手 ID（建议返回，便于排查） |
| `username` | string \| null | 否 | 用户名（建议返回） |
| `nickname` | string \| null | 否 | 昵称（建议返回） |
| `score` | number | 是 | 总分。后端按整数读取（`asInt()`），建议返回整数 |
| `score_details` | object | 否 | 各题明细，key 必须是题目 `pid` |

### 3.2 `score_details[pid]` 子字段

| 字段名 | 类型 | 是否必须 | 说明 |
|---|---|---|---|
| `score` | number | 否 | 该题得分（后端按整数读取） |
| `accepted` | boolean | 否 | 是否通过 |
| `unacceptedCount` | number | 否 | 未通过提交次数 |
| `acceptedTime` | number | 否 | 通过时间（时间戳或你自定义数值） |
| `weighted_score` | number | 否 | 加权分 |
| `judge_id` | number | 否 | 用于标识该题对应提交 ID |

### 3.3 最小合法示例

只返回总分也可被解析：

```json
[
  {
    "score": 100
  }
]
```

### 3.4 完整示例

```json
[
  {
    "user_id": 10001,
    "username": "alice",
    "nickname": "Alice",
    "score": 123,
    "score_details": {
      "A": {
        "score": 100,
        "accepted": true,
        "unacceptedCount": 0,
        "acceptedTime": 123456,
        "weighted_score": 100,
        "judge_id": 123
      }
    }
  }
]
```

说明：
- 记录顺序会被后端保留并写入 rank（第 1 条即第 1 名）。
- `score_details` 的 key 必须是题目 `pid`，否则前端无法按题渲染。
- `score`、`score_details[*].score` 若返回小数，后端会按整数读取（可能截断），建议脚本内先自行取整。

---

## 4. 最小模板

```js
function getRequiredSubmissionFields() {
  return ["id", "score", "verdict", "submit_time"];
}

function computeStandings(input) {
  const rows = [];

  for (const [uid, user] of Object.entries(input.users || {})) {
    let total = 0;
    const details = {};

    for (const p of input.problems || []) {
      const pid = p.pid;
      const subs = (user.submissions && user.submissions[pid]) || [];

      let best = 0;
      for (const s of subs) {
        const sc = Number(s.score || 0);
        if (sc > best) best = sc;
      }

      total += best;
      details[pid] = { score: best };
    }

    rows.push({
      user_id: Number(uid),
      username: user.username || null,
      nickname: user.nickname || null,
      score: Math.round(total),
      score_details: details
    });
  }

  rows.sort((a, b) => b.score - a.score);
  return rows;
}
```

---

## 5. 常见错误

- `computeStandings` 不存在：会执行失败。
- `getRequiredSubmissionFields` 返回非数组或数组内非字符串：会报参数错误。
- 返回排行榜不是数组：会报“自定义脚本必须返回数组”。
- 访问未声明且未回退包含的字段：可能得到 `undefined`。
