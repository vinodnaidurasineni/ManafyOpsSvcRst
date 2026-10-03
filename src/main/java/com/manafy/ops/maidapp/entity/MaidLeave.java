package com.manafy.ops.maidapp.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A maid's leave request (ported from ManafySvcRst). Approved/rejected by admin.
 */
@Entity
@Table(name = "maidapp_maid_leave", indexes = {
        @Index(name = "idx_mleave_maid", columnList = "maid_id")
})
public class MaidLeave extends BaseEntity {

    @Column(name = "maid_id", nullable = false)
    private UUID maidId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(length = 255)
    private String reason;

    @Column(name = "leave_status", nullable = false, length = 20)
    private String leaveStatus; // PENDING, APPROVED, REJECTED

    public UUID getMaidId() { return maidId; }
    public void setMaidId(UUID maidId) { this.maidId = maidId; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getLeaveStatus() { return leaveStatus; }
    public void setLeaveStatus(String leaveStatus) { this.leaveStatus = leaveStatus; }
}
