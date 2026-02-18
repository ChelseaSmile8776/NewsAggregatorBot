# Этап 1: Сборка JAR файла
FROM gradle:jdk17-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Собираем JAR, пропуская тесты (чтобы быстрее)
RUN gradle bootJar --no-daemon -x test

# Этап 2: Запуск
FROM openjdk:17-slim
EXPOSE 8080
# Копируем JAR из первого этапа
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
