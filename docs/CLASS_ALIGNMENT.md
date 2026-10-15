# Revisión técnica de clase — 15/10/2026

Esta revisión está probada localmente. No demuestra despliegue en Azure ni cambio de configuración en Render. Publicación, PR y despliegue requieren confirmación posterior del propietario.

## Cambios y recorrido del código

1. `NotificationProcessor` es el adaptador Kafka. `NotificationEventRequestMapper` traduce el JSON legacy o DomainEvent v1 a `ProcessNotificationCommand`.
2. `ProcessNotificationEventUseCase` es el puerto de entrada; `NotificationEventApplicationService` implementa la decisión de aplicación. El servicio no conoce Kafka, JSON ni HTTP.
3. `NotificationEmailMapper` selecciona plantilla y produce `EmailDestinationModel`. `UserEmail` es el value object que normaliza y valida formato antes de acceder al proveedor; `InvalidUserEmailException` e `InvalidEmailDestinationException` expresan invariantes sin incluir PII.
4. `EmailSenderPort` reside en dominio. `BrevoEmailSenderAdapter` implementa ese puerto y `EmailConfiguration` configura el cliente externo. El eventId sigue siendo la clave estable de idempotencia.
5. `user.deleted` con userId se reconoce y retorna IGNORED: cero llamadas a Brevo, cero SENT, FAILED o DLQ para esa eliminación válida. Un evento desconocido o una eliminación sin userId sigue siendo inválido. El contrato de eliminación no necesita nombre ni email.

Se mantiene Kafka, no RabbitMQ. La rama legacy permanece compatible para poder desplegar primero el consumidor sin interrumpir los productores existentes. El modo tipado de usuarios evita que ese productor genere HTML o llame directamente a Brevo.

## Evidencia reproducible

- `mvn clean verify`: 25 pruebas aprobadas, ninguna omitida en esta revisión. Incluye HTTP real a un proveedor simulado en loopback y pruebas del adaptador Brevo.
- `python scripts/smoke_fanin.py`: PostgreSQL, Kafka y tres aplicaciones reales en Docker; WireMock sustituye exclusivamente a Brevo. No usa claves de producción.
- Smoke aprobado: usuario creado, cuenta creada y usuario actualizado generan tres correos simulados. Eliminar el usuario no genera un cuarto. Email inválido no llega al proveedor; la DLQ contiene solo metadatos seguros. Ambas outboxes se vacían.
- Usuarios: 297 pruebas aprobadas, incluidas transacciones PostgreSQL con Testcontainers que demuestran commit y rollback conjunto de eliminación/outbox.
- Cuentas: 11 pruebas aprobadas; el cambio de este repositorio acompañante sincroniza el contrato user.deleted, no altera la operación de creación de cuentas.

## Orden de publicación posterior a revisión

1. Revisar los commits locales de los tres repositorios y sus contratos compartidos.
2. Publicar y desplegar primero notificaciones, todavía compatible con legacy.
3. Aplicar/controlar la migración y permisos de outbox de usuarios y seguir `docs/DOMAIN_EVENTS_ACTIVATION.md` del repositorio de usuarios. Los flags en render.yaml son una configuración preparada, no una modificación automática del servicio existente.
4. Desplegar/activar el productor tipado con el worker embebido y envío directo deshabilitados.
5. Repetir una prueba de nube autorizada y correlacionar persistencia, outbox, eventId, consumo y proveedor. El email simulado local no prueba entrega real.

## Límites

No se afirma exactly-once global ni alta disponibilidad con una réplica. La validación de email comprueba el subconjunto sintáctico admitido, no existencia del buzón. Kafka conserva registros conforme a retención; confirmar offsets no los borra. SENT significa aceptación, no entrega humana. El cambio no introduce una inbox durable ni elimina las dependencias síncronas del servicio de cuentas.
