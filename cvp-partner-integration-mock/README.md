# Partner Integration Mock

Development-only, in-memory simulation of the OEM partner integration boundary. `POST /api/v1/bookings` returns a request reference only; it never confirms an appointment. Operators use `/mock/v1/bookings/{reference}/confirm` and `/complete` to invoke Appointment callbacks later. State resets on restart. Swagger UI is `/swagger-ui.html`.
