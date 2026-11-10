FROM eclipse-temurin:21-jre
RUN useradd --system --create-home mcp
USER mcp
WORKDIR /app
COPY --chown=mcp:mcp build/libs/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","/app/app.jar"]
