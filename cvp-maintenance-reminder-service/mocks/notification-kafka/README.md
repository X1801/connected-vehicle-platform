# Kafka-capable Mock Notification Service

This lightweight local integration component consumes `cvp.notification-request.v1` and publishes a `DELIVERED` event to `cvp.notification-status.v1` using the existing Maintenance Reminder Service JSON contracts.

Configuration is environment based:

- `KAFKA_BOOTSTRAP_SERVERS` (default `kafka:9092`)
- `KAFKA_CONSUMER_GROUP` (default `notification-mock`)
- `NOTIFICATION_REQUEST_TOPIC` (default `cvp.notification-request.v1`)
- `NOTIFICATION_STATUS_TOPIC` (default `cvp.notification-status.v1`)
