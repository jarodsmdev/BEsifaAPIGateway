```markdown
# API Gateway - Servicio de Enrutamiento y Autenticación

## 📋 Descripción

API Gateway desarrollado con Spring Cloud Gateway que actúa como punto único de entrada para una arquitectura de microservicios. Proporciona enrutamiento inteligente, validación de tokens JWT y headers de auditoría para los servicios downstream.

## 🏗️ Arquitectura

```
Cliente → API Gateway (9000) → Microservicios
├── Auth Service (8081)
├── Plate Service (8000)
└── Core Service (8083)
```

## 🚀 Características

- **Enrutamiento dinámico**: Redirección basada en paths hacia los microservicios correspondientes
- **Validación JWT**: Verificación local de tokens sin dependencia del auth-service
- **Headers de auditoría**: Sustituye cualquier `X-Auth-*` entrante por valores verificados y añade `X-Auth-Identity` (JWT interno) en peticiones autenticadas
- **Canal interno**: Añade `X-Internal-Key` a cada petición reenviada, para que los servicios bajos puedan descartar llamadas que no vengan del gateway
- **Rutas públicas**: `/auth/api/v1/**` y la documentación de Swagger sin validación de token
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
JWT_SECRET=<openssl rand -hex 32>
JWT_ISSUER=sifa-clients
JWT_AUDIENCE=sifa-clients
INTERNAL_JWT_SECRET=<openssl rand -hex 32>   # debe ser el mismo en auth y core
INTERNAL_JWT_ISSUER=sifa-gateway
INTERNAL_JWT_AUDIENCE=sifa-core
INTERNAL_CHANNEL_KEY=<openssl rand -hex 32>  # debe ser el mismo en auth, core y plate
AUTH_SERVICE_URL=http://localhost:8081
PLATE_SERVICE_URL=http://localhost:8000
CORE_SERVICE_URL=http://localhost:8083
```

> `INTERNAL_JWT_SECRET` y `INTERNAL_CHANNEL_KEY` son secretos compartidos: el
> valor tiene que ser idéntico en gateway, auth y core (y `INTERNAL_CHANNEL_KEY`
> también en plate), porque uno lo firma y el resto lo verifica.

### application.properties

El listado `spring.cloud.gateway.routes[]` está en el fichero
[`src/main/resources/application.properties`](src/main/resources/application.properties).
Los índices deben ser contiguos: si se añade o elimina una ruta hay que
renumerar el resto, porque Spring enlaza la propiedad como `List` y un hueco
deja un `null` que acaba en un `NPE`.

```properties
spring.cloud.gateway.routes[0].id=auth-service
spring.cloud.gateway.routes[0].uri=${AUTH_SERVICE_URL}
spring.cloud.gateway.routes[0].predicates[0]=Path=/auth/api/v1/**

spring.cloud.gateway.routes[1].id=plate-service
spring.cloud.gateway.routes[1].uri=${PLATE_SERVICE_URL}
spring.cloud.gateway.routes[1].predicates[0]=Path=/plate/api/v1/**
```

## 🗺️ Rutas

| Ruta | Método | Protección | Destino |
|------|--------|------------|---------|
| `/auth/api/v1/**` | * | Pública | Auth Service (8081) |
| `/plate/api/v1/**` | * | JWT | Plate Service (8000) |
| `/core/api/v1/**` | * | JWT | Core Service (8083) |
| `/core/v3/api-docs`, `/auth/v3/api-docs`, `/plate/openapi.json` | GET | Pública | Documentación de cada servicio |

Todas las rutas protegidas aceptan el token desde la cookie httpOnly
`access_token` o, como fallback para apps móviles, desde
`Authorization: Bearer <token>`.

## 🔐 Headers hacia Microservicios

| Header | Valor | Descripción |
|--------|-------|-------------|
| `Authorization` | `Bearer {token}` | Token JWT original |
| `X-Auth-User` | `{username}` | Username extraído del token |
| `X-Auth-Roles` | `{rol1,rol2}` | Roles extraídos del token |
| `X-Auth-Token-Valid` | `true` | Indicador de validación exitosa |
| `X-Auth-Identity` | `{JWT interno}` | JWT de 60 s firmado por el gateway; es el único que core-sifa acepta como identidad |
| `X-Internal-Key` | `{INTERNAL_CHANNEL_KEY}` | Firma del canal interno; lo añade el gateway y lo exigen los servicios bajos |

Las cabeceras `X-Auth-*` que lleguen del cliente **se eliminan siempre** antes de
reenviar, tanto en rutas públicas como protegidas, para que nadie pueda colar una
identidad forjada llegando directo al servicio.

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
- Microservicios destino en puertos 8081, 8000, 8083 (configurable)

```

Este README profesional incluye toda la información necesaria para entender, configurar y ejecutar el gateway. ¿Necesitas ajustar algún detalle?
