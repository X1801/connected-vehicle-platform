FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/cvp-partner-integration-mock-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
