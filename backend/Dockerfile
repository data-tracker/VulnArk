# Java 后端：Maven 构建 + JRE 运行
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS builder

# 国内网络使用阿里云 Maven 镜像加速；settings 放 /tmp（.m2 将作为缓存挂载点）
RUN mkdir -p /tmp/mvn && cat > /tmp/mvn/settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

WORKDIR /build
COPY pom.xml .
# 使用 BuildKit 缓存挂载：下载失败重试时可复用已下载的依赖（应对不稳定网络）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /tmp/mvn/settings.xml dependency:go-offline -q || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /tmp/mvn/settings.xml package -DskipTests -q

########## 运行 ##########
FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y --no-install-recommends wget tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r vulnark && useradd -r -g vulnark vulnark

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
USER vulnark

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/api/v3/api-docs || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
