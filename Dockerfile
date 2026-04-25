# syntax=docker/dockerfile:1.7

# --- 第一阶段：构建阶段 ---
# 使用官方 Maven 镜像作为构建环境 (包含 JDK 21)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build-stage

# 设置工作目录
WORKDIR /app

# 配置 Maven 阿里云镜像
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>\
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"\
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">\
  <mirrors>\
    <mirror>\
      <id>aliyun</id>\
      <mirrorOf>central</mirrorOf>\
      <url>https://maven.aliyun.com/repository/central</url>\
    </mirror>\
  </mirrors>\
</settings>' > /root/.m2/settings.xml

# 1. 优化：先只复制 pom.xml，下载所有依赖和插件（利用 Docker 缓存）
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B


# 2. 复制源码并进行打包（依赖层已缓存，-o 离线模式避免再次检查）
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -Dmaven.test.skip=true

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
