```markdown
# API Gateway - Servicio de Enrutamiento y Autenticación

## 📋 Descripción

API Gateway desarrollado con Spring Cloud Gateway que actúa como punto único de entrada para una arquitectura de microservicios. Proporciona enrutamiento inteligente, validación de tokens JWT y headers de auditoría para los servicios downstream.

## 🏗️ Arquitectura

```
Cliente → API Gateway (8080) → Microservicios
├── Auth Service (8081)
├── Plate Service (8082)
└── Dashboard Service (8083)
```

## 🚀 Características

- **Enrutamiento dinámico**: Redirección basada en paths hacia los microservicios correspondientes
- **Validación JWT**: Verificación local de tokens sin dependencia del auth-service
- **Headers de auditoría**: Inyección de `X-Auth-User` y `X-Auth-Token-Valid` en peticiones autenticadas
- **Rutas públicas**: `/auth/login` y `/auth/register` sin validación de token
- **Manejo de errores**: Respuestas amigables para servicios no disponibles
- **Métricas de tiempo**: Logging de duración de peticiones

## 📦 Tecnologías

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| Spring Boot | 3.5.13 | Framework principal |
| Spring Cloud Gateway | 4.3.4 | Enrutamiento reactivo |
| JJWT | 0.12.6 | Validación de tokens JWT |
| Maven | - | Gestión de dependencias |
| Java | 17 | Lenguaje base |

## 🔧 Configuración

### Variables de entorno (.env)

```bash
# .env
JWT_SECRET=lTt8Yu2kri039ApfbTcY6Omiq8cCfCb8uLsE5DZS+oY=
AUTH_SERVICE_URL=http://localhost:8081
PLATE_SERVICE_URL=http://localhost:8082
DASHBOARD_SERVICE_URL=http://localhost:8083
```

### application.properties

```properties
spring.application.name=${APP_NAME:gateway}
server.port=${SERVER_PORT:8080}
jwt.secret=${JWT_SECRET}

# Enrutamiento
spring.cloud.gateway.server.webflux.routes[0].id=auth-service
spring.cloud.gateway.server.webflux.routes[0].uri=${AUTH_SERVICE_URL}
spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/auth/**

spring.cloud.gateway.server.webflux.routes[1].id=plate-service
spring.cloud.gateway.server.webflux.routes[1].uri=${PLATE_SERVICE_URL}
spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/api/v1/plate/**

spring.cloud.gateway.server.webflux.routes[2].id=dashboard-service
spring.cloud.gateway.server.webflux.routes[2].uri=${DASHBOARD_SERVICE_URL}
spring.cloud.gateway.server.webflux.routes[2].predicates[0]=Path=/api/v1/dashboard/**
```

## 🗺️ Rutas

| Ruta | Método | Protección | Destino |
|------|--------|------------|---------|
| `/auth/login` | POST | Pública | Auth Service (8081) |
| `/auth/register` | POST | Pública | Auth Service (8081) |
| `/api/v1/plate/**` | * | JWT | Plate Service (8082) |
| `/api/v1/dashboard/**` | * | JWT | Dashboard Service (8083) |

## 🔐 Headers hacia Microservicios

| Header | Valor | Descripción |
|--------|-------|-------------|
| `Authorization` | `Bearer {token}` | Token JWT original |
| `X-Auth-User` | `{username}` | Username extraído del token |
| `X-Auth-Token-Valid` | `true` | Indicador de validación exitosa |

## 📊 Respuestas de Error

| Código | Error | Descripción |
|--------|-------|-------------|
| 401 | No autorizado | Token inválido o no proporcionado |
| 503 | Servicio no disponible | Microservicio destino no está corriendo |
| 504 | Gateway Timeout | Timeout en comunicación con microservicio |

### Ejemplo respuesta 503

```json
{
    "timestamp": "2026-04-05T04:44:53.633+00:00",
    "path": "/auth/login",
    "status": 503,
    "error": "Servicio no disponible",
    "message": "El servicio de autenticación no está disponible"
}
```

## 🚀 Ejecución

### Desarrollo

```bash
# Clonar repositorio
git clone <repository-url>
cd gateway

# Configurar variables de entorno
cp .env.example .env
# Editar .env con valores locales

# Ejecutar con Maven
mvn spring-boot:run

# O usando script con carga de .env
source .env && mvn spring-boot:run
```

### Producción

```bash
# Compilar
mvn clean package

# Ejecutar JAR
java -jar target/gateway-0.0.1-SNAPSHOT.jar
```

## 🧪 Pruebas

### Login (ruta pública)

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

### Ruta protegida con token

```bash
TOKEN="eyJhbGciOiJIUzI1NiIs..."

curl -X GET http://localhost:8080/api/v1/plate/detect \
  -H "Authorization: Bearer ${TOKEN}"
```

## 📝 Logging

Formato de logs con métricas de tiempo:

```
[+] Ruta pública: /auth/login
[➡️] POST http://localhost:8080/auth/login
[⬅️] Status: 200 OK - Tiempo: 142 ms

[!] Ruta protegida: /api/v1/plate/detect
[+] Token válido para: jarod
[➡️] GET http://localhost:8080/api/v1/plate/detect
[⬅️] Status: 200 OK - Tiempo: 45 ms
```

## 📁 Estructura del Proyecto

```
gateway/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/evecta/gateway/
│   │   │       ├── GatewayApplication.java
│   │   │       ├── config/
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── filter/
│   │   │       │   └── JwtAuthenticationFilter.java
│   │   │       ├── util/
│   │   │       │   └── JwtUtil.java
│   │   │       └── exception/
│   │   │           └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── .env
├── pom.xml
└── README.md
```

## ⚙️ Requisitos

- Java 17+
- Maven 3.6+
- Microservicios destino en puertos 8081, 8082, 8083 (configurable)

```

Este README profesional incluye toda la información necesaria para entender, configurar y ejecutar el gateway. ¿Necesitas ajustar algún detalle?