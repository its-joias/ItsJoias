# Back-end do ItsJoias para o Render.
# 1ª etapa: compila com Maven (Java 21). 2ª etapa: imagem pequena só com o Java para rodar.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/itsjoias-0.0.1-SNAPSHOT.jar app.jar
# Plano gratuito do Render: 512 MB. O Java usa até 75% disso para a memória da aplicação.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
