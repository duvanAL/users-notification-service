# Servicio de notificaciones

Consumidor independiente del patrón fan-in. Recibe user.created, user.updated y account.created
desde Kafka/Aiven, prepara el correo y lo envía por Brevo.
Reconoce `user.deleted` con `userId` y lo omite intencionalmente: no envía email, SENT ni DLQ
para un evento válido. Mantiene lectura de solicitudes legacy pendientes durante la transición.
Adapta la arquitectura del [ejemplo del profesor](https://github.com/arrietajohn/notification-service)
a las tecnologías ya utilizadas, manteniendo Java 17 y Spring Boot 3.3.5.

## Separación
[Usuarios](https://github.com/duvanAL/users-management-spring-boot-hexagonal-ddd) y
[cuentas](https://github.com/duvanAL/users-accounts-service) son dos productores independientes.
Este proyecto no contiene CRUD de usuarios, acceso PostgreSQL, JWT, Valkey ni Supabase Storage.
Los pocos adaptadores Brevo reutilizados conservan su paquete original por trazabilidad,
pero sus fuentes están aquí y no requieren dependencias de otro repositorio.

## Recorrido hexagonal

`NotificationProcessor` (adaptador de entrada Kafka) usa `NotificationEventRequestMapper`
para construir un `ProcessNotificationCommand` inmutable. Invoca el puerto de entrada
`ProcessNotificationEventUseCase`, implementado por `NotificationEventApplicationService`.
La aplicación valida el tipo y usa `NotificationEmailMapper` para obtener el objeto de dominio
`EmailDestinationModel`. El puerto de salida `domain.port.out.EmailSenderPort` es implementado
por `BrevoEmailSenderAdapter`. Jackson/Kafka no aparecen en el caso de uso ni en el dominio.

`UserEmail` normaliza y valida el formato antes de invocar el proveedor; errores de destinatario
son excepciones de dominio sin el valor privado en el mensaje. La expresión regular define el
subconjunto de direcciones soportadas, no verifica la existencia del buzón. `EmailDestinationModel`
valida los campos restantes y excluye su contenido de `toString`.

## Kafka y procesamiento
Los tres topics existentes se conservan. [Contrato y ejemplos](contracts/README.md).
Entrada compartida: user.notification.requested. Grupo: notify-service.
Salidas: user.notification.result y user.notification.dlq.
Un único consumidor lógico recibe ambos tipos de productor.

- Confirmación por registro después de completar el envío y publicar el resultado.
- Reintentos limitados y circuito de protección para Brevo.
- Si no puede publicar la DLQ/resultado, el error se propaga; no se descarta silenciosamente.
- Fallos terminales se convierten en metadatos DLQ, sin copiar datos personales.
- eventId se usa como clave de idempotencia en Brevo; no se garantiza exactamente una vez
  fuera de la ventana de deduplicación del proveedor.
- Compatibilidad de lectura con NotificationRequestMessage anterior para drenar el outbox legado.
- Los offsets del grupo sobreviven al reinicio según Kafka. No confundir commit con borrado del mensaje.

GET /health y /actuator/health/liveness comprueban el proceso, no la entrega de correo.
Para probar las dependencias se debe observar consumo, resultado y recepción del correo.
No hay endpoint público para enviar correos ni consultar mensajes.

## Variables requeridas
Copiar .env.example para Docker o usar secretos del proveedor.
KAFKA_BOOTSTRAP_SERVERS, KAFKA_USERNAME, KAFKA_PASSWORD,
KAFKA_SSL_TRUST_CERTIFICATES (PEM CA de Aiven), BREVO_API_KEY y BREVO_FROM_ADDRESS.
El remitente debe estar verificado en Brevo. BREVO_FROM_NAME es opcional.
Producción usa SASL_SSL/SCRAM-SHA-256 con verificación del hostname.
La identidad notify-service necesita READ en el topic de entrada y grupo notify-service,
y WRITE en result y dlq; configurar Host * para las conexiones de la plataforma.

## Ejecución
```powershell
.\mvnw.cmd -B clean verify
docker build -t users-notification-service .
docker run --rm --env-file .env -p 8082:8082 users-notification-service
```
Linux: ./mvnw -B clean verify.
.env no se carga automáticamente con java -jar; exportar variables o usar --env-file.

## Prueba local completa sin credenciales reales
Clonar los tres repositorios como carpetas hermanas con sus nombres de GitHub.
La API de usuarios debe incluir la rama de integración de eventos de dominio.
Requiere Docker Compose y Python 3. Desde este repo:
```
python scripts/smoke_fanin.py
```
Levanta temporalmente los tres servicios, PostgreSQL, Kafka y un Brevo simulado.
Crea un usuario, inicia sesión, crea una cuenta y actualiza el usuario: verifica tres correos
simulados con eventId diferentes. Elimina solo el usuario desechable y verifica omisión sin email;
inyecta un email inválido en la outbox del PostgreSQL local y verifica DLQ de metadatos.
Confirma que ambos outboxes se vacían.
Al finalizar elimina sus contenedores de prueba.
No usa Aiven, Azure, claves reales ni envía correos.
USERS_SOURCE y ACCOUNTS_SOURCE permiten indicar otras rutas de clones.
Los puertos locales 18080, 18081, 18082 y 18089 deben estar libres.
El perfil local habilita PLAINTEXT exclusivamente para ese broker aislado.

## Preparado para Azure for Students
Actions -> Publish container publica en ghcr.io/duvanal/users-notification-service.
Usar el tag del commit y configurar la visibilidad/acceso del registro.
Escucha PORT (8082 por defecto); la aplicación es un servicio HTTP con consumidor Kafka interno.
Para mantenerlo activo fijar mínimo 1 y máximo 1 réplica. Serverless no significa costo cero 24/7:
revisar el consumo del crédito de estudiante.
La creación del recurso y los secretos queda para el siguiente paso con la suscripción activa.

## Transición
Seguir docs/transition.md para evitar dos consumidores incompatibles en el mismo grupo.
No activar eventos de dominio mientras el worker legado sigue consumiendo en la API.
Los nuevos repositorios se publican sin cambiar los servicios actualmente desplegados.
