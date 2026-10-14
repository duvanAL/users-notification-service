# Transición a tres servicios

Código publicado no equivale a desplegado. Estos pasos se ejecutan después de crear los recursos Azure.

1. Integrar la PR de eventos de dominio en la rama deploy/render de usuarios.
   El valor predeterminado APP_DOMAIN_EVENTS_ENABLED=false preserva el flujo actual.
2. Aplicar en PostgreSQL el SQL V20261013_01__create_domain_event_outbox.sql del repo de usuarios.
   Aplicar también schema.sql de cuentas con sus credenciales/schema propio.
3. Crear/configurar la identidad Kafka accounts-service y verificar las ACL mínimas.
   Conservar las identidades users-api y notify-service y la CA TLS Aiven.
4. Desplegar notificaciones separado. Puede consumir el formato legado, con mismo grupo notify-service.
5. En la API de usuarios fijar APP_KAFKA_IN_PROCESS_WORKER_ENABLED=false, manteniendo
   APP_DOMAIN_EVENTS_ENABLED=false. Fijar también APP_EMAIL_ENABLED=false: los eventos
   siguen habilitados con Kafka y Brevo ahora pertenece al servicio separado.
   Esperar despliegue completado y salida del proceso anterior.
6. Solo entonces activar APP_DOMAIN_EVENTS_ENABLED=true. APP_KAFKA_ENABLED debe seguir true.
   La API encola eventos de dominio y deja de generar un correo legado para esa misma operación.
7. Desplegar cuentas con PostgreSQL, USERS_API_URL y JWT_SECRET compartido con usuarios.
   Mantener worker de notificaciones activo (mínimo 1 réplica) y comprobar que no compite con un worker viejo.
8. Probar registro usuario -> correo; creación cuenta -> segundo correo, log de ambos eventName,
   resultados Kafka, outbox vacío y ausencia de contraseñas/tokens en mensajes.
9. Probar desconexión/reconexión en un entorno de prueba. Kafka retiene registros durante 72h
   y confirma mediante offsets; no reproduce el borrado inmediato de RabbitMQ.

Para revertir los productores, desactivar APP_DOMAIN_EVENTS_ENABLED.
El consumidor nuevo acepta ambos formatos, así que puede seguir atendiendo notificaciones legadas.
No borrar tablas/topics mientras haya eventos pendientes.

No se configura Azure ni se amplían ACLs desde estos archivos.
No se requiere eliminar el Kafka o la infraestructura que ya funciona.
