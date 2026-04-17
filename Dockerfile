# -------- STAGE 1: Build --------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copiar pom y descargar dependencias (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiar el código fuente
COPY src ./src

# Compilar el proyecto
RUN mvn clean package -DskipTests

# -------- STAGE 2: Runtime --------
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copiar el jar generado desde el stage anterior
COPY --from=builder /app/target/*.jar app.jar

# Exponer el puerto
EXPOSE 9000

# Variables por defecto (opcional)
ENV SERVER_PORT=9000

# Ejecutar la app
ENTRYPOINT ["java","-jar","/app/app.jar"]