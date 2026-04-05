# 测试框架文档总览

## 目标
- 以集成测试为主，覆盖核心业务链路与关键异常分支。
- 所有测试用例必须可追踪到规范文档，禁止无意义测试。
- 已报告 Bug 必须有回归用例并登记到回归索引。

## 运行方式
- 测试环境使用 `test` profile。
- 测试数据库在测试开始和结束时都会执行重置脚本：`src/test/resources/sql/test-reset.sql`。
- 本地运行：
```bash
./mvnw test "-Dspring.profiles.active=test"
```

## 目录说明
- 统一用例目录（命名+场景+预期）：`docs/testing/test-case-catalog.md`
- 核心链路规范：`docs/testing/specs/core-flows.md`
- 鉴权与题目管理规范：`docs/testing/specs/auth-and-problem-management.md`
- 回归索引：`docs/testing/regression-index.md`

## 用例命名规范
- 命名格式：`<层级>-<模块>-<编号>`
- 层级取值：
  - `INT`：集成测试
  - `REG`：回归测试
- 模块建议：
  - `AUTH`、`PROBLEM`、`SUBMISSION`
- 示例：
  - `INT-AUTH-001`
  - `INT-SUBMISSION-003`
  - `REG-AUTH-GH001-001`

## 断言规范
- 异常场景至少断言两层：
  - HTTP 状态码
  - 业务 `code`
- 当前统一错误码语义：
  - 参数错误：`40000`
  - 未登录：`40100`
  - 无权限：`40300`
  - 资源不存在：`40400`
  - 业务冲突：`40900`

## 数据与实现约束
- 测试数据通过 SQL 重置脚本 + Mapper/Bean 组合维护。
- 测试脚本内禁止拼接原生 SQL 字符串。
- 涉及数据库状态变化的用例，必须增加持久化结果断言。
