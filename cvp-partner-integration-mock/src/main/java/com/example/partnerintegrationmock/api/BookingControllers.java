package com.example.partnerintegrationmock.api;
import com.example.partnerintegrationmock.application.MockBookingService; import com.example.partnerintegrationmock.model.MockBooking; import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController public class BookingControllers {
 private final MockBookingService service; public BookingControllers(MockBookingService s){service=s;}
 @PostMapping("/api/v1/bookings") ResponseEntity<SubmissionResponse> submit(@Valid @RequestBody SubmissionRequest r,@RequestHeader(value="X-Correlation-Id",required=false) String correlationId){var b=service.submit(r.appointmentId,r.vehicleId,r.dealerId,r.dealerName,r.slotId,r.serviceType);return ResponseEntity.accepted().header("X-Correlation-Id",correlationId==null?"":correlationId).body(new SubmissionResponse(true,b.partnerRequestReference));}
 @GetMapping("/mock/v1/bookings") Collection<View> list(){return service.list().stream().map(View::new).toList();}
 @GetMapping("/mock/v1/bookings/{reference}") View get(@PathVariable String reference){return new View(service.get(reference));}
 @PostMapping("/mock/v1/bookings/{reference}/confirm") View confirm(@PathVariable String reference){return new View(service.confirm(reference));}
 @PostMapping("/mock/v1/bookings/{reference}/complete") View complete(@PathVariable String reference){return new View(service.complete(reference));}
 record SubmissionRequest(UUID appointmentId,@NotBlank String vehicleId,@NotBlank String dealerId,@NotBlank String dealerName,@NotBlank String slotId,@NotBlank String serviceType){} record SubmissionResponse(boolean accepted,String partnerRequestReference){}
 record View(UUID appointmentId,String partnerRequestReference,String externalDmsReference,String vehicleId,String dealerId,String dealerName,String slotId,String serviceType,MockBooking.State mockDealerState,MockBooking.Delivery confirmCallbackState,MockBooking.Delivery completeCallbackState,String lastCallbackError){View(MockBooking b){this(b.appointmentId,b.partnerRequestReference,b.externalDmsReference,b.vehicleId,b.dealerId,b.dealerName,b.slotId,b.serviceType,b.state,b.confirmCallbackState,b.completeCallbackState,b.lastCallbackError);}}
}
