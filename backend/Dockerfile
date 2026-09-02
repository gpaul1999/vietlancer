# ---- Build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Tách bước tải dependency để tận dụng cache Docker (chỉ chạy lại khi đổi build script)
COPY gradlew ./
COPY gradle gradle
RUN chmod +x gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime ----
# Nếu registry chưa có tag 25-jre, đổi sang eclipse-temurin:25-jdk
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl cho HEALTHCHECK; chạy bằng user không phải root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /usr/sbin/nologin vietlancer \
    && mkdir -p /app/uploads && chown -R vietlancer:vietlancer /app

COPY --from=build --chown=vietlancer:vietlancer /app/build/libs/*.jar app.jar

USER vietlancer
EXPOSE 8080
VOLUME ["/app/uploads"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Container đã giới hạn RAM → để JVM tự đọc cgroup limit
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
