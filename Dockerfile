# --- 第一阶段：构建阶段 ---
# 使用官方 Maven 镜像作为构建环境 (包含 JDK 21)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build-stage

# 设置工作目录
WORKDIR /app

# 1. 优化：先只复制 pom.xml，下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline

# 2. 复制源码并进行打包
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# --- 第二阶段：运行阶段 ---
# 使用轻量级的 JRE 21 镜像
FROM eclipse-temurin:21-jre-alpine AS production-stage

WORKDIR /app

# 从构建阶段拷贝生成的 jar 包
# 注意：生成的 jar 包名称通常在 pom.xml 中定义，通常在 target 目录下
COPY --from=build-stage /app/target/*.jar app.jar

# 暴露 Spring Boot 默认端口 (根据你实际修改)
EXPOSE 8080

# 运行 jar 包
ENTRYPOINT ["java", "-jar", "app.jar"]
