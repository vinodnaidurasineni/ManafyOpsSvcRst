package com.manafy.ops.maidapp.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CheckOutRequest {

    @NotNull(message = "attendanceId is required")
    private UUID attendanceId;

    public UUID getAttendanceId() { return attendanceId; }
    public void setAttendanceId(UUID attendanceId) { this.attendanceId = attendanceId; }
}
