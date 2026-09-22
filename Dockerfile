# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package
RUN case "$(uname -m)" in \
      x86_64) sqlite_arch=x86_64 ;; \
      aarch64|arm64) sqlite_arch=aarch64 ;; \
      *) echo "Unsupported SQLite JDBC Linux architecture: $(uname -m)" >&2; exit 1 ;; \
    esac \
    && mkdir -p /workspace/sqlite-native \
    && cd /workspace/sqlite-native \
    && jar xf /root/.m2/repository/org/xerial/sqlite-jdbc/3.50.1.0/sqlite-jdbc-3.50.1.0.jar "org/sqlite/native/Linux/$sqlite_arch/libsqlitejdbc.so" \
    && mv "org/sqlite/native/Linux/$sqlite_arch/libsqlitejdbc.so" libsqlitejdbc.so \
    && rm -rf org

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 dba-mcp \
    && useradd --uid 10001 --gid dba-mcp --no-create-home --shell /usr/sbin/nologin dba-mcp \
    && mkdir -p /config /data/assets /data/known-hosts /data/audit /tmp/dba-mcp /opt/sqlite \
    && chown -R dba-mcp:dba-mcp /data /tmp/dba-mcp
WORKDIR /app
COPY --from=build /workspace/target/dba-mcp-*.jar /app/dba-mcp.jar
COPY --from=build /workspace/sqlite-native/libsqlitejdbc.so /opt/sqlite/libsqlitejdbc.so
USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=http \
    DBA_HTTP_ADDRESS=0.0.0.0 \
    DBA_HTTP_PORT=8080 \
    DBA_ASSETS_JDBC_URL=jdbc:sqlite:/data/assets/dba-mcp-assets.db \
    DBA_ASSETS_READ_ONLY=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp/dba-mcp -Dorg.sqlite.lib.path=/opt/sqlite -Dorg.sqlite.lib.name=libsqlitejdbc.so"
ENTRYPOINT ["java", "-jar", "/app/dba-mcp.jar"]
