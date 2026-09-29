package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.maidapp.entity.BookingAssignment;
import com.manafy.ops.maidapp.entity.BookingSchedule;
import com.manafy.ops.maidapp.entity.MaidLeave;
import com.manafy.ops.maidapp.repository.BookingAssignmentRepository;
import com.manafy.ops.maidapp.repository.BookingScheduleRepository;
import com.manafy.ops.maidapp.repository.MaidLeaveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Admin assignment + leave operations (ManafyOps compatibility layer).
 * Manual-assign / reassign a maid to a booking schedule, approve/reject leave.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAssignmentController {

    private final BookingAssignmentRepository assignmentRepository;
    private final BookingScheduleRepository scheduleRepository;
    private final MaidLeaveRepository leaveRepository;

    public AdminAssignmentController(BookingAssignmentRepository assignmentRepository,
                                     BookingScheduleRepository scheduleRepository,
                                     MaidLeaveRepository leaveRepository) {
        this.assignmentRepository = assignmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.leaveRepository = leaveRepository;
    }

    @PostMapping("/assignments")
    public ApiResponse<Map<String, String>> manualAssign(@RequestBody Map<String, String> body) {
        UUID bookingScheduleId = UUID.fromString(body.get("bookingScheduleId"));
        UUID maidId = UUID.fromString(body.get("maidId"));

        BookingSchedule schedule = scheduleRepository.findByIdAndDeletedFalse(bookingScheduleId)
                .orElseThrow(() -> new BusinessException("ASSIGN_001", "Schedule not found", HttpStatus.NOT_FOUND));

        BookingAssignment a = new BookingAssignment();
        a.setBookingScheduleId(bookingScheduleId);
        a.setMaidId(maidId);
        a.setAssignedAt(LocalDateTime.now());
        a.setAssignedBy("ADMIN");
        a.setAssignmentStatus("ASSIGNED");
        a.setCreatedBy("ADMIN");
        a = assignmentRepository.save(a);

        schedule.setStatus("ASSIGNED");
        scheduleRepository.save(schedule);

        return ApiResponse.ok(Map.of("assignmentId", a.getId().toString()));
    }

    @PostMapping("/assignments/{assignmentId}/reassign")
    public ApiResponse<Map<String, String>> reassign(@PathVariable UUID assignmentId,
                                                      @RequestBody Map<String, String> body) {
        BookingAssignment old = assignmentRepository.findByIdAndDeletedFalse(assignmentId)
                .orElseThrow(() -> new BusinessException("ASSIGN_004", "Assignment not found", HttpStatus.NOT_FOUND));
        old.setAssignmentStatus("CANCELLED");
        assignmentRepository.save(old);

        BookingAssignment neu = new BookingAssignment();
        neu.setBookingScheduleId(old.getBookingScheduleId());
        neu.setMaidId(UUID.fromString(body.get("maidId")));
        neu.setAssignedAt(LocalDateTime.now());
        neu.setAssignedBy("ADMIN");
        neu.setAssignmentStatus("REASSIGNED");
        neu.setCreatedBy("ADMIN");
        neu = assignmentRepository.save(neu);
        return ApiResponse.ok(Map.of("assignmentId", neu.getId().toString()));
    }

    @PostMapping("/leaves/{leaveId}/approve")
    public ApiResponse<Map<String, String>> approveLeave(@PathVariable UUID leaveId) {
        setLeaveStatus(leaveId, "APPROVED");
        return ApiResponse.ok(Map.of("status", "APPROVED"));
    }

    @PostMapping("/leaves/{leaveId}/reject")
    public ApiResponse<Map<String, String>> rejectLeave(@PathVariable UUID leaveId) {
        setLeaveStatus(leaveId, "REJECTED");
        return ApiResponse.ok(Map.of("status", "REJECTED"));
    }

    private void setLeaveStatus(UUID leaveId, String status) {
        MaidLeave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new BusinessException("LEAVE_404", "Leave not found", HttpStatus.NOT_FOUND));
        leave.setLeaveStatus(status);
        leave.setUpdatedBy("ADMIN");
        leaveRepository.save(leave);
    }
}
