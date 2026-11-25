# Imagem de producao da API do Frota+ (Spring Boot 4, Java 21).
# Nenhum segredo aqui: DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET,
# CORS_ORIGENS_PERMITIDAS e o SMTP vem do ambiente da hospedagem
# (lista em .env.example e docs/DEPLOYMENT_PRODUCTION.md).
#
# Build:  docker build -t frotamais-api .
# Rodar:  docker run --env-file <arquivo-fora-do-git> -p 8080:8080 frotamais-api

# --- Etapa 1: compila o jar (os testes rodam no CI e no Codespaces) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp clean package -DskipTests \
    && cp target/backend-*.jar app.jar

# --- Etapa 2: so o JRE e o jar, rodando sem root ---
FROM eclipse-temurin:21-jre
RUN groupadd --system frotamais && useradd --system --gid frotamais --no-create-home frotamais
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER frotamais
# Perfil de producao explicito; GuardaProducao confere banco e CORS ao subir.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
