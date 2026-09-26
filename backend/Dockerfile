ARG DOCKER_REGISTRY=
FROM ${DOCKER_REGISTRY}maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml settings.xml ./
RUN mvn -s settings.xml -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -s settings.xml -q -DskipTests package

FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8182

ENTRYPOINT ["java", "-jar", "app.jar"]
