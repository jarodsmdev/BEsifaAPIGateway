package com.evecta.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.lang.NonNull;
import java.util.Objects;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Order(-2)
public class GatewayExcepcionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExcepcionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @NonNull
    @SuppressWarnings("null")
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("path", exchange.getRequest().getURI().getPath());

        HttpStatus status;

        // Caso específico: Connection refused (tu error actual)
        if (ex instanceof ConnectException ||
                (ex.getMessage() != null && ex.getMessage().contains("Connection refused"))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorDetails.put("status", 503);
            errorDetails.put("error", "Servicio no disponible");
            errorDetails.put("message",
                    "El servicio de autenticación no está disponible. ¿Está corriendo el auth-service en el puerto 8081?");
            log.error("[!] Auth-service no disponible en puerto 8081");
        }
        // Timeout
        else if (ex.getMessage() != null && ex.getMessage().contains("timeout")) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            errorDetails.put("status", 504);
            errorDetails.put("error", "Gateway Timeout");
            errorDetails.put("message", "El servicio destino no respondió a tiempo");
        } else if (ex instanceof NoResourceFoundException) {
            status = HttpStatus.NOT_FOUND;
            errorDetails.put("status", 404);
            errorDetails.put("error", "Not Found");
            errorDetails.put("message", "No existe ruta para este endpoint en el gateway");
        }
        // Otros errores
        else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorDetails.put("status", 500);
            errorDetails.put("error", "Error interno");
            errorDetails.put("message", "Ha ocurrido un error interno en el gateway");
            log.error("[!] Error no manejado: ", ex.getMessage());
        }

        try {
            byte[] bytes = Objects.requireNonNull(objectMapper.writeValueAsBytes(errorDetails));
            DataBuffer buffer = Objects.requireNonNull(response.bufferFactory().wrap(bytes));
            response.setStatusCode(status);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("[!] Error serializando respuesta de error", e);
            return Mono.error(e);
        }
    }
}