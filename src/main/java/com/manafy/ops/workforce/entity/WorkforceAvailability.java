package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

/** Basic workforce availability (Phase 3 §17). No scheduling engine. */
@Entity
@Table(name = "workforce_availability")
public class WorkforceAvailability extends BaseEntity {

    @Column(name = "workforce_kind", nullable = false, length = 20)
    private String workforceKind;   // TECHNICIAN | HELPER

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "helper_id")
    private UUID helperId;

    /** 0-6 for a weekly window; null when specificDate is used. */
    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "start_time", length = 8)
    private String startTime;   // HH:mm[:ss]

    @Column(name = "end_time", length = 8)
    private String endTime;

    @Column(name = "is_leave", nullable = false)
    private boolean leave = false;

    @Column(length = 255)
    private String note;

    public String getWorkforceKind() { return workforceKind; }
    public void setWorkforceKind(String workforceKind) { this.workforceKind = workforceKind; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public Short getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Short dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalDate getSpecificDate() { return specificDate; }
    public void setSpecificDate(LocalDate specificDate) { this.specificDate = specificDate; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public boolean isLeave() { return leave; }
    public void setLeave(boolean leave) { this.leave = leave; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
