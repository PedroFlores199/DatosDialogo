FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build /source/target/datos-dialogo-1.0.0.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx640m -Djava.awt.headless=true"
EXPOSE 5104
ENTRYPOINT ["java","-jar","/app/app.jar"]
