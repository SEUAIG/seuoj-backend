# SEUOJ Backend

基于 Spring Boot 3.2 / Java 21 / MyBatis Plus / MySQL，默认端口 `8080`。

## 环境要求
- JDK 21
- Maven 3.9+（已提供 `mvnw`/`mvnw.cmd`）
- MySQL 8.0+
- Docker（可选，用于镜像构建与运行）

## 配置（YML 或环境变量）
Spring Boot 读取优先级：环境变量 > 命令行参数 > `application-*.yml`。可按需要选择其一或组合使用，`SPRING_PROFILES_ACTIVE` 用于指定加载的 profile（默认 dev）。

**方式 1：YML 配置**
1. 复制模板，删除.template后缀：
   ```bash
   cp src/main/resources/application-dev.yml.template src/main/resources/application-dev.yml
   # 需要 prod/test 时复制对应模板
   ```
2. 在 `application-<profile>.yml` 中填写数据库、JWT、判题服务等字段。
3. 启动时通过 `SPRING_PROFILES_ACTIVE=<profile>` 或 `--spring.profiles.active=<profile>` 选择配置。

**方式 2：环境变量（推荐生产环境）**
按下列变量配置，即可覆盖 YML：
```
# 应用配置
SPRING_APPLICATION_NAME=seuoj-backend

# 数据库配置
SPRING_DATASOURCE_URL=jdbc:mysql://<DB_HOST>:3306/<DB_NAME>?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=<DB_USERNAME>
SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver

# MyBatis Plus 配置
MYBATIS_PLUS_CONFIGURATION_MAP_UNDERSCORE_TO_CAMEL_CASE=true
MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_DELETE_FIELD=is_del
MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_DELETE_VALUE=1
MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_NOT_DELETE_VALUE=0
MYBATIS_PLUS_MAPPER_LOCATIONS=classpath:/mapper/**/*.xml
MYBATIS_PLUS_LOG_IMPL=org.apache.ibatis.logging.stdout.StdOutImpl

# JWT 配置
JWT_SECRET=<JWT_SECRET>
JWT_EXPIRATION=86400

# 评测服务配置
JUDGE_SERVER_URL=http://<JUDGE_HOST>
JUDGE_SECRET=<JUDGE_SECRET>

# 日志配置
LOGGING_FILE_NAME=/var/log/backend/backend.log
LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE=10MB
LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY=7

# 代码存储配置
STORAGE_USER_CODE_STORAGE_PATH: ./data/user-code
```

## 初始化数据库
```bash
mysql -u <user> -p<pass> < sql/database_schema.sql
# 如需示例数据
mysql -u <user> -p<pass> < sql/database_init_data.sql
```

## 本地运行
```bash
./mvnw spring-boot:run
# Windows:
mvnw.cmd spring-boot:run
```
健康检查：`http://localhost:8080/actuator/health`。

## 构建与测试
- 运行测试：`./mvnw test`
- 构建（跳过测试）：`./mvnw clean package -DskipTests`，产物位于 `target/*.jar`

## 注意事项
- 不要提交包含密码/密钥的配置文件，生产环境使用环境变量或安全的配置中心。
- 提交前建议运行 `./mvnw test`，确保基础用例通过。
