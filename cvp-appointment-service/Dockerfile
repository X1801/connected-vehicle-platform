FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/cvp-appointment-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
