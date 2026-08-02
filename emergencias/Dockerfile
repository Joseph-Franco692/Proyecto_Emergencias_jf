FROM maven:3.9.12-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml mvnw.cmd ./
COPY .mvn .mvn
RUN mvn -B -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/uploads && chown -R spring:spring /app
COPY --from=build --chown=spring:spring /app/target/*.jar /app/app.jar
USER spring
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

