# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 dba-mcp \
    && useradd --uid 10001 --gid dba-mcp --no-create-home --shell /usr/sbin/nologin dba-mcp \
    && mkdir -p /data/assets /data/known-hosts /data/audit /tmp/dba-mcp \
    && chown -R dba-mcp:dba-mcp /data /tmp/dba-mcp
WORKDIR /app
COPY --from=build /workspace/target/dba-mcp-*.jar /app/dba-mcp.jar
USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=http \
    DBA_HTTP_ADDRESS=0.0.0.0 \
    DBA_HTTP_PORT=8080 \
    DBA_ASSETS_JDBC_URL=jdbc:sqlite:/data/assets/dba-mcp-assets.db \
    DBA_ASSETS_READ_ONLY=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp/dba-mcp"
ENTRYPOINT ["java", "-jar", "/app/dba-mcp.jar"]
