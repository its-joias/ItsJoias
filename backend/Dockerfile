# Back-end do ItsJoias para o Render.
# 1ª etapa: compila com Maven (Java 21). 2ª etapa: imagem pequena só com o Java para rodar.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# Leitura de PDF da importação de maletas (programas gratuitos, rodam aqui dentro, nada sai do servidor):
# poppler-utils = texto e imagem das páginas; tesseract (+ português) = OCR de PDF escaneado/foto.
RUN apt-get update \
    && apt-get install -y --no-install-recommends poppler-utils tesseract-ocr tesseract-ocr-por \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/itsjoias-0.0.1-SNAPSHOT.jar app.jar
# Plano gratuito do Render: 512 MB. O Java fica com até 50% (256 MB); o resto é do próprio Java
# fora da heap e do Tesseract (~130 MB) durante a leitura de um PDF escaneado.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m"
ENV OMP_THREAD_LIMIT=1
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
