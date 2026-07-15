FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S studystack && adduser -S studystack -G studystack
WORKDIR /app

COPY --from=build /workspace/target/studystack-server-0.0.1.jar /app/app.jar

USER studystack
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
