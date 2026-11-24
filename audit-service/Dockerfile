# syntax=docker/dockerfile:1.7
# Build from the repository root:  docker build -f audit-service/Dockerfile -t claimflow/audit-service .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY member-service/pom.xml member-service/
COPY claims-service/pom.xml claims-service/
COPY audit-service/pom.xml audit-service/
COPY common/src common/src
COPY audit-service/src audit-service/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl audit-service -am package -DskipTests
RUN java -Djarmode=tools -jar audit-service/target/audit-service-*.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --uid 10001 app
WORKDIR /app
# Least frequently changing layers first so rebuilds reuse them.
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 10001
EXPOSE 8183
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
