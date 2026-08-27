FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S fincore && adduser -S fincore -G fincore
WORKDIR /app

COPY --from=build --chown=fincore:fincore /workspace/target/*.jar /app/app.jar

USER fincore
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
