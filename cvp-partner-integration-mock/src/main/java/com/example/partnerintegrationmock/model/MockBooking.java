package com.example.partnerintegrationmock.model;
import java.util.UUID;
public class MockBooking {
 public enum State { RECEIVED, CONFIRMED, COMPLETED } public enum Delivery { NOT_SENT, DELIVERED, FAILED }
 public final UUID appointmentId; public final String vehicleId,dealerId,dealerName,slotId,serviceType,partnerRequestReference; public String externalDmsReference,lastCallbackError; public State state=State.RECEIVED; public Delivery confirmCallbackState=Delivery.NOT_SENT, completeCallbackState=Delivery.NOT_SENT;
 public MockBooking(UUID id,String vehicle,String dealer,String name,String slot,String type,String ref){appointmentId=id;vehicleId=vehicle;dealerId=dealer;dealerName=name;slotId=slot;serviceType=type;partnerRequestReference=ref;}
 public boolean same(UUID id,String vehicle,String dealer,String name,String slot,String type){return appointmentId.equals(id)&&vehicleId.equals(vehicle)&&dealerId.equals(dealer)&&dealerName.equals(name)&&slotId.equals(slot)&&serviceType.equals(type);}
}
