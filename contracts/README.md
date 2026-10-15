# Contrato Kafka v1

Dos productores independientes convergen en un consumidor:
- users-management: user.created, user.updated y user.deleted.
- users-accounts-service: account.created.
- users-notification-service: transforma esos eventos en correo Brevo.

Se reutiliza el topic existente `user.notification.requested` como entrada compartida de fan-in.
Aunque su nombre conserva el prefijo user, recibe eventos de ambos servicios.
No se requieren topics nuevos. Los otros topics siguen siendo `user.notification.result` y `user.notification.dlq`.

El cuerpo es JSON UTF-8 y la key es eventId. Se ignoran los headers de tipos Java:
ningún servicio depende del classpath o del paquete Java del otro.
Schema y ejemplos se incluyen en esta carpeta. eventId permanece estable en cada reintento.
Los eventos nunca contienen contraseñas, tokens JWT ni claves.

Un resultado conserva el formato anterior:
`{"schemaVersion":1,"notificationId":"UUID del evento","status":"SENT","processedAt":"fecha ISO","failureCode":null}`.
Para fallo terminal se publica FAILED y un código, junto a metadatos en DLQ.
La DLQ nueva no copia el correo ni el HTML original.

user.deleted lleva solo userId: el consumidor lo reconoce e ignora intencionalmente sin
enviar correo, publicar SENT/FAILED ni producir DLQ para un evento válido. No necesita
name/email; los otros eventos conservan esos campos obligatorios. Un nombre de evento
desconocido o un deleted sin userId sigue siendo un error, no una omisión silenciosa.

Kafka retiene los registros según su retención (actualmente 72h). Confirmar el procesamiento
avanza el offset del grupo; no elimina el registro inmediatamente.
El outbox conserva eventos pendientes hasta que Kafka confirma su publicación.
Esto no promete entrega exactamente una vez. Brevo recibe eventId como idempotencyKey;
su deduplicación tiene una ventana limitada y no sustituye un registro permanente de entregas.

Para cambiar el contrato, actualizar las copias del schema, fixtures y record en los tres repositorios.
No introducir campos incompatibles sin versionar. No se publica una biblioteca Maven privada.
