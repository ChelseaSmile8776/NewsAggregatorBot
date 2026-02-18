FROM eclipse-temurin:17-jre-alpine
EXPOSE 8080
# Копируем уже собранный JAR файл из папки build
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
