# SEUOJ-Backend 开发文档

欢迎来到 SEUOJ-Backend 项目！本文档旨在帮助新加入的开发者快速熟悉项目、了解开发规范并高效地参与协作。

## 1. 快速上手 (Getting Started)

本章节将指导你如何在本地配置并成功运行项目。

### 1.1. 环境依赖 (Prerequisites)

请确保你的开发环境满足以下要求：

- **JDK 21**: 项目基于 Java 21。
- **Maven 3.6+**: 用于项目构建和依赖管理。
- **MySQL 8.0+**: 项目使用的数据库。
- **IDE**: 推荐使用 `IntelliJ IDEA` 。

### 1.2. 项目配置 (Configuration)

1. **克隆项目**:
   ```bash
   git clone <your-repository-url>
   cd seuoj-backend
   ```

2. **配置文件**:
   项目的核心配置文件位于 `src/main/resources/` 目录下。为了区分不同环境，我们使用 `.yml.template` 模板。
   对于本地开发，请复制 `application-dev.yml.template` 并重命名为 `application-dev.yml`。
   ```bash
   cp src/main/resources/application-dev.yml.template src/main/resources/application-dev.yml
   ```
   然后，根据你的本地环境修改 `application-dev.yml` 中的配置，主要包括：
    - **数据库连接**: `spring.datasource.url`, `username`, `password`。
    - **JWT 密钥**: `jwt.secret` (建议修改为一个复杂的随机字符串)。

3. **数据库初始化**:
   使用你的数据库管理工具（如 Navicat, DBeaver）连接到你的 MySQL 数据库，并执行 `sql/database_schema.sql`
   脚本。这将创建项目所需的数据库和表结构。(可以运行 `sql/database_init_data.sql` 插入一些示例数据)

### 1.3. 运行项目 (Running the Application)

- **通过 Maven 运行**:
  在项目根目录下，执行以下命令：
  ```bash
  ./mvnw spring-boot:run
  ```

- **通过 IDE 运行**:
  直接在 `IntelliJ IDEA` 中打开项目，它会自动识别为 Maven 项目。等待依赖下载完成后，找到 `SeuojBackendApplication.java`
  文件，右键点击并选择 `Run 'SeuojBackendApplication'`。

项目成功启动后，默认将在 `8080` 端口监听服务。

## 2. 项目结构概览 (Project Structure)

了解项目结构有助于你快速定位代码和理解功能模块。

```
src/main/java/com/seuoj/seuojbackend/
├── annotation/      # 自定义注解 (如 @RequireRole 权限控制)
├── aspect/          # AOP 切面 (如 AuthAspect 用于实现权限校验逻辑)
├── common/          # 通用类、枚举和常量 (如 Result, ErrorCode, RoleType)
├── config/          # Spring Boot 配置类 (如 MybatisPlus, Security, WebMvc)
├── controller/      # API 接口层 (接收前端请求, 调用 Service)
├── dto/             # 数据传输对象 (Data Transfer Object, 用于接收前端参数)
├── entity/          # 数据库实体类 (与数据库表一一对应)
├── exception/       # 自定义异常及全局异常处理器
├── interceptor/     # 拦截器 (如 JwtAuthInterceptor 解析 Token)
├── mapper/          # MyBatis-Plus Mapper 接口 (定义数据库操作)
├── service/         # 业务逻辑层 (实现核心业务)
├── util/            # 工具类 (如 JwtUtil)
└── vo/              # 视图对象 (View Object, 返回给前端的数据)
```

### 如何使用已有脚手架

- **统一响应**: 所有 Controller 接口都应返回 `Result` 对象，确保前后端数据格式统一。使用 `Result.success(data)` 和
  `Result.error(code, message)`。
- **权限控制**:
    - **公开接口**: 在 Controller 方法上添加 `@AllowAnonymous` 注解，该接口将放行所有请求不进行任何权限校验，注意这个注解具备最高优先级。
    - **需要登录**: 默认需要登录。`JwtAuthInterceptor` 会自动校验。
    - **需要特定角色**: 在 Controller 方法上添加 `@RequireRole("admin")`，`AuthAspect` 会检查用户角色。
- **用户信息**: 在需要登录的接口中（在整个请求的生命周期内的所有地方都可以用这个方法获取，如果涉及异步可能会出现问题，后续再说），可以通过
  `UserContextHolder.get()` 获取当前登录用户的线程上下文信息 （为 UserContext 对象）。
- **异常处理**: 业务代码中，对于可以预见的错误直接 `throw` 定义在 `exception` 包中的特定业务异常（如
  `BadRequestException`），`GlobalExceptionHandler` 会捕获并返回统一格式的错误信息。

### 2.1. MyBatis-Plus 配置及使用

本项目集成了 MyBatis-Plus 框架，并配置了如下常用功能：

#### a. 自动填充 (Auto-Fill)

- **功能**: 用于自动填充实体类中的 `created_at` (或 `createdAt`) 和 `updated_at` (或 `updatedAt`) 字段。
- **实现**: `com.seuoj.seuojbackend.config.MyMetaObjectHandler` 类负责在插入和更新操作时自动设置 `LocalDateTime.now()` 到这些字段。
- **使用**: 你无需手动设置这些字段。在插入或更新操作时，MyBatis-Plus 会自动处理。请确保你的实体类中包含这些字段，并且类型为 `LocalDateTime`。

#### b. 软删除 (Soft Delete)

- **功能**: 通过 `is_del` 字段标记数据为已删除，而不是真正从数据库中删除，便于数据恢复和历史追溯。
- **实现**:
    - **数据库字段**: 所有需要软删除的表都包含一个 `TINYINT(1)` 类型的 `is_del` 字段，默认值为 `0` (未删除)。
    - **实体类**: 在实体类的 `is_del` (或 `deleted`) 字段上添加 `@TableLogic` 注解。
    - **MybatisPlusConfig**: 配置了 `logic-delete-field: is_del`。
- **使用**:
    - **查询**: 当你执行查询操作时 (如 `selectById`, `selectList`)，MyBatis-Plus 会自动在 SQL 中添加 `WHERE is_del = 0` 的条件，只返回未删除的数据。
    - **删除**: 调用 `mapper.deleteById(id)` 或 `mapper.delete(wrapper)` 方法时，MyBatis-Plus 会自动执行 `UPDATE table_name SET is_del = 1 WHERE id = ?`，而不是 `DELETE` 语句。
    - **恢复/真实删除**: 如果需要查询已删除数据或进行真实删除，需要绕过 MyBatis-Plus 的逻辑，例如使用自定义 SQL 或特定的查询Wrapper。一般情况下，强烈建议只使用软删除。

#### c. 分页插件 (Pagination Plugin)

- **功能**: 方便地实现数据库层面的物理分页。
- **实现**: `com.seuoj.seuojbackend.config.MybatisPlusConfig` 配置了 `MybatisPlusInterceptor` 来注册分页插件。
- **使用**:
    1.  在 Service 层，创建一个 `Page<T>` 对象作为参数传入 Mapper 方法：
        ```java
        Page<User> page = new Page<>(current_page, page_size); // current_page: 当前页码, page_size: 每页记录数
        IPage<User> userPage = userMapper.selectPage(page, null); // 第二个参数是查询条件Wrapper
        ```
    2.  Mapper 方法的返回值应为 `IPage<T>` 类型：
        ```java
        IPage<User> selectPage(Page<User> page, @Param(Constants.WRAPPER) Wrapper<User> queryWrapper);
        ```
    3.  Controller 层可以直接返回 `IPage<T>` 对象给前端，其中包含了分页所需的所有信息（总记录数、总页数、当前页数据等）。



## 3. 开发规范 (Development Conventions)

遵循统一的规范是高效协作的基础。

### 3.1. Git 工作流 (Git Workflow)

1. **分支模型**:
    - `master`: 主分支，保持稳定，用于部署生产环境。
    - `feat/xxx`: 功能开发分支，`xxx` 应简要描述功能，如 `feat/user-management`。
    - `fix/xxx`: Bug 修复分支，如 `fix/login-error`。
    - `refactor/xxx`: 重构分支。

2. **开发流程**:
    - 从 `master` 分支创建新的 `feat` 或 `fix` 分支。
    - 完成开发后，提交代码并推送到远程仓库。
    - 创建一个 Pull Request (PR) 到 `master` 分支。
    - 至少需要一名其他开发者（目前看来很有可能是copilot）进行 Code Review，通过后方可合并。

3. **Commit 消息规范**:
   遵循 `type(scope): subject` 的格式。
    - `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore` 等。
    - `scope`: 可选，表示影响范围（如 `auth`, `problem`）。
    - `subject`: 简明扼要地描述本次提交的内容。
    - 示例: `feat(auth): add user registration endpoint`
    - 不知道怎么写安一个ai插件让ai写

### 3.2. 编码规范 (Coding Style)

- **命名**:
    - **包名**: 全小写，使用单数形式，如 `com.seuoj.seuojbackend.problem`。
    - **类/接口**: `PascalCase`，如 `UserInfo`, `AuthService`。
    - **方法/变量**: `camelCase`，如 `getUserById`。
    - **常量**: `UPPER_SNAKE_CASE`，如 `DEFAULT_PAGE_SIZE`。
    - **DTO/VO**: 以 `DTO` 和 `VO` 结尾，如 `LoginDTO`, `UserVO`。
- **代码风格**:
    - 遵循项目已有的代码风格。
    - 建议使用 IDE 的自动格式化功能。
    - 添加必要的注释，尤其是复杂的业务逻辑。包括类/方法上的 Javadoc（必须有），以及方法内的单行注释（可选，如果方法复杂还是写一下）

### 3.3. API 设计 (API Design)

- 按照 Apifox 里面的定义来写就行。感觉有问题修改后群里通知。（写的时候容易笔误）
- **参数校验**: 对于接收前端参数的 DTO 对象，务必使用 JSR 303 (Bean Validation) 规范进行参数校验。在 DTO 字段上添加校验注解（如 `@NotBlank`, `@NotNull`, `@Size` 等），并在 Controller 方法参数前添加 `@Valid` 注解来触发校验。

## 4. 开发流程 (Development Process)

一个典型的功能开发流程如下：

1. **创建分支**: 从 `master` 创建 `feat/your-feature` 分支。
2. **实现业务逻辑**: 在 `service` 包中编写接口和实现类。
3. **创建 DTO/VO**: 定义用于数据传输的 DTO 和返回给前端的 VO。
4. **暴露 API**: 在 `controller` 包中创建 `Controller`，定义 API 接口，并调用 `Service`。
5. **权限控制**: 在 `Controller` 方法上添加必要的权限注解。
6. **本地测试**: 使用 Apifox 或其他工具进行接口自测。**请务必在 Apifox 中将成功的测试结果保存为样例，以便后续生成 Mock 数据和进行自动化测试。**
7. **代码格式化**: 对你的代码进行完全的格式化！（我用idea的，推荐写完 alt + A 后 alt + L 顺手格式化，或者commit的时候统一对要提交的文件进行格式化）
8. **提交PR**: 提交代码并发起 Pull Request。
9. **Code Review**: 等待其他成员审查代码。
10. **合并**: PR 通过后，合并到 `master` 分支。

## 5. 时区管理 (Timezone Management)

为简化开发和避免跨时区问题，本项目统一使用 **上海时间 (Asia/Shanghai)** 进行所有时间相关的操作。

- **JVM 默认时区**: 已通过代码设置为 `Asia/Shanghai`。
- **Spring Jackson 序列化/反序列化**: 已配置为 `Asia/Shanghai`。
- **MySQL 数据库连接**: 连接 URL 中 `serverTimezone` 参数已设置为 `Asia/Shanghai`。
- **数据库字段类型**: 时间相关字段使用 `DATETIME` 类型，直接存储上海本地时间。
- **MyBatis-Plus 自动填充**: `LocalDateTime.now()` 将获取并使用上海本地时间进行填充。

**重要提示**: 本项目不考虑跨时区问题。所有时间操作和显示都将以 `Asia/Shanghai` 为准。

---
祝你编码愉快！


