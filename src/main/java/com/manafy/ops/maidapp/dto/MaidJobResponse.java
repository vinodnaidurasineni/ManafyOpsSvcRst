package com.manafy.ops.maidapp.dto;

import java.time.LocalDate;

/** A single job (service occurrence) shown in the maid app. Ported from ManafySvcRst. */
public class MaidJobResponse {

    private String assignmentId;
    private String scheduleId;
    private LocalDate scheduledDate;
    private String slotId;
    private String slotTime;
    private String serviceName;
    private String apartmentName;
    private String tower;
    private String flatNumber;
    private String status;
    private boolean customerUnavailable;

    public MaidJobResponse(String assignmentId, String scheduleId, LocalDate scheduledDate, String slotId,
                           String serviceName, String apartmentName, String tower, String flatNumber, String status) {
        this.assignmentId = assignmentId;
        this.scheduleId = scheduleId;
        this.scheduledDate = scheduledDate;
        this.slotId = slotId;
        this.serviceName = serviceName;
        this.apartmentName = apartmentName;
        this.tower = tower;
        this.flatNumber = flatNumber;
        this.status = status;
    }

    public String getAssignmentId() { return assignmentId; }
    public String getScheduleId() { return scheduleId; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public String getSlotId() { return slotId; }
    public String getSlotTime() { return slotTime; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }
    public boolean isCustomerUnavailable() { return customerUnavailable; }
    public void setCustomerUnavailable(boolean customerUnavailable) { this.customerUnavailable = customerUnavailable; }
    public String getServiceName() { return serviceName; }
    public String getApartmentName() { return apartmentName; }
    public String getTower() { return tower; }
    public String getFlatNumber() { return flatNumber; }
    public String getStatus() { return status; }
}
