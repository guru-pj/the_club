# Build the jar first from the project root: mvn clean install -DskipTests
FROM eclipse-temurin:22-jre-jammy

# Run as an unprivileged user
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --chown=app:app target/*.jar app.jar
USER app

EXPOSE 8888
# Container-sized heap; override with JAVA_TOOL_OPTIONS
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# The JRE image ships no curl/wget, so probe /actuator/health with bash's /dev/tcp instead of adding packages.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8888 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"status\":\"UP\"" <&3'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
