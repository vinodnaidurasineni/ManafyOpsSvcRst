package com.manafy.ops.maidapp.service;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.maidapp.dto.ApplyLeaveRequest;
import com.manafy.ops.maidapp.dto.CheckInRequest;
import com.manafy.ops.maidapp.dto.MaidJobResponse;
import com.manafy.ops.maidapp.entity.Attendance;
import com.manafy.ops.maidapp.entity.Booking;
import com.manafy.ops.maidapp.entity.BookingAssignment;
import com.manafy.ops.maidapp.entity.BookingSchedule;
import com.manafy.ops.maidapp.entity.MaidLeave;
import com.manafy.ops.maidapp.repository.AttendanceRepository;
import com.manafy.ops.maidapp.repository.BookingAssignmentRepository;
import com.manafy.ops.maidapp.repository.BookingRepository;
import com.manafy.ops.maidapp.repository.BookingScheduleRepository;
import com.manafy.ops.maidapp.repository.MaidLeaveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Maid App: today's jobs + dashboard counts (ported from ManafySvcRst
 * MaidAppService). Display fields come denormalized from BookingSchedule, so no
 * address/apartment/catalog lookups are needed for this slice.
 */
@Service
public class MaidAppService {

    private final BookingAssignmentRepository assignmentRepository;
    private final BookingScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final AttendanceRepository attendanceRepository;
    private final MaidLeaveRepository leaveRepository;

    public MaidAppService(BookingAssignmentRepository assignmentRepository,
                          BookingScheduleRepository scheduleRepository,
                          BookingRepository bookingRepository,
                          AttendanceRepository attendanceRepository,
                          MaidLeaveRepository leaveRepository) {
        this.assignmentRepository = assignmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.bookingRepository = bookingRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
    }

    /** Today's jobs for the maid — assignments whose schedule date is today. */
    public List<MaidJobResponse> getTodayJobs(UUID maidId) {
        return getJobsForDate(maidId, LocalDate.now());
    }

    public List<MaidJobResponse> getJobsForDate(UUID maidId, LocalDate date) {
        List<BookingAssignment> assignments = assignmentRepository.findByMaidIdAndDeletedFalse(maidId);
        List<MaidJobResponse> jobs = new ArrayList<>();

        for (BookingAssignment a : assignments) {
            if ("CANCELLED".equals(a.getAssignmentStatus())) continue;

            BookingSchedule schedule = scheduleRepository.findByIdAndDeletedFalse(a.getBookingScheduleId())
                    .orElse(null);
            if (schedule == null || !schedule.getScheduledDate().equals(date)) continue;

            MaidJobResponse job = new MaidJobResponse(
                    a.getId().toString(),
                    schedule.getId().toString(),
                    schedule.getScheduledDate(),
                    schedule.getSlotId() != null ? schedule.getSlotId().toString() : null,
                    schedule.getServiceName(),
                    schedule.getApartmentName(),
                    schedule.getTower(),
                    schedule.getFlatNumber(),
                    schedule.getStatus());

            // Slot time: prefer the schedule's, else the booking's selectedTime.
            String slotTime = schedule.getSlotTime();
            if (slotTime == null && schedule.getBookingId() != null) {
                Booking booking = bookingRepository.findByIdAndDeletedFalse(schedule.getBookingId()).orElse(null);
                if (booking != null) slotTime = booking.getSelectedTime();
            }
            job.setSlotTime(slotTime);
            jobs.add(job);
        }
        return jobs;
    }

    /** Dashboard counts for today. */
    public Map<String, Object> getDashboard(UUID maidId) {
        List<MaidJobResponse> today = getTodayJobs(maidId);
        long completed = today.stream().filter(j -> "COMPLETED".equals(j.getStatus())).count();
        return Map.of(
                "todayAssignments", today.size(),
                "completedAssignments", completed,
                "pendingAssignments", today.size() - completed);
    }

    // ---- Attendance ----

    @Transactional
    public Attendance checkIn(UUID maidId, CheckInRequest request) {
        LocalDate today = LocalDate.now();
        attendanceRepository.findByMaidIdAndAttendanceDateAndDeletedFalse(maidId, today)
                .ifPresent(a -> { throw new BusinessException("ATTEND_001", "Already checked in today", HttpStatus.CONFLICT); });

        Attendance attendance = new Attendance();
        attendance.setMaidId(maidId);
        attendance.setAttendanceDate(today);
        attendance.setCheckinTime(LocalDateTime.now());
        attendance.setLatitude(request.getLatitude());
        attendance.setLongitude(request.getLongitude());
        attendance.setStatus("PRESENT");
        attendance.setCreatedBy(maidId.toString());
        return attendanceRepository.save(attendance);
    }

    @Transactional
    public Attendance checkOut(UUID maidId, UUID attendanceId) {
        Attendance attendance = attendanceRepository.findByIdAndDeletedFalse(attendanceId)
                .orElseThrow(() -> new BusinessException("ATTEND_002", "Attendance not found", HttpStatus.NOT_FOUND));
        if (!attendance.getMaidId().equals(maidId)) {
            throw new BusinessException("ATTEND_003", "Attendance does not belong to you", HttpStatus.FORBIDDEN);
        }
        attendance.setCheckoutTime(LocalDateTime.now());
        attendance.setUpdatedBy(maidId.toString());
        return attendanceRepository.save(attendance);
    }

    // ---- Leave ----

    @Transactional
    public UUID applyLeave(UUID maidId, ApplyLeaveRequest request) {
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new BusinessException("LEAVE_001", "toDate cannot be before fromDate", HttpStatus.BAD_REQUEST);
        }
        MaidLeave leave = new MaidLeave();
        leave.setMaidId(maidId);
        leave.setFromDate(request.getFromDate());
        leave.setToDate(request.getToDate());
        leave.setReason(request.getReason());
        leave.setLeaveStatus("PENDING");
        leave.setCreatedBy(maidId.toString());
        return leaveRepository.save(leave).getId();
    }

    public List<MaidLeave> getLeaves(UUID maidId) {
        return leaveRepository.findByMaidIdAndDeletedFalse(maidId);
    }

    // ---- Service start / complete ----

    @Transactional
    public void startService(UUID maidId, UUID assignmentId) {
        BookingAssignment assignment = getOwnedAssignment(maidId, assignmentId);
        assignment.setAssignmentStatus("IN_PROGRESS");
        assignmentRepository.save(assignment);

        BookingSchedule schedule = scheduleRepository.findByIdAndDeletedFalse(assignment.getBookingScheduleId())
                .orElseThrow(() -> new BusinessException("ASSIGN_001", "Schedule not found", HttpStatus.NOT_FOUND));
        schedule.setStatus("IN_PROGRESS");
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void completeService(UUID maidId, UUID assignmentId) {
        BookingAssignment assignment = getOwnedAssignment(maidId, assignmentId);
        assignment.setAssignmentStatus("COMPLETED");
        assignmentRepository.save(assignment);

        BookingSchedule schedule = scheduleRepository.findByIdAndDeletedFalse(assignment.getBookingScheduleId())
                .orElseThrow(() -> new BusinessException("ASSIGN_001", "Schedule not found", HttpStatus.NOT_FOUND));
        schedule.setStatus("COMPLETED");
        scheduleRepository.save(schedule);
    }

    private BookingAssignment getOwnedAssignment(UUID maidId, UUID assignmentId) {
        BookingAssignment assignment = assignmentRepository.findByIdAndDeletedFalse(assignmentId)
                .orElseThrow(() -> new BusinessException("ASSIGN_004", "Assignment not found", HttpStatus.NOT_FOUND));
        if (!assignment.getMaidId().equals(maidId)) {
            throw new BusinessException("ASSIGN_005", "Assignment does not belong to you", HttpStatus.FORBIDDEN);
        }
        return assignment;
    }

    // ---- Earnings (ported from ManafySvcRst) ----

    public Map<String, Object> getEarnings(UUID maidId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        List<BookingAssignment> allAssignments = assignmentRepository.findByMaidIdAndDeletedFalse(maidId);

        Set<UUID> activeBookingIds = new HashSet<>();
        BigDecimal estimatedMonthly = BigDecimal.ZERO;
        for (BookingAssignment a : allAssignments) {
            if ("CANCELLED".equals(a.getAssignmentStatus())) continue;
            BookingSchedule schedule = scheduleRepository.findByIdAndDeletedFalse(a.getBookingScheduleId()).orElse(null);
            if (schedule == null || schedule.getScheduledDate().isBefore(monthStart)) continue;
            Booking booking = bookingRepository.findByIdAndDeletedFalse(schedule.getBookingId()).orElse(null);
            if (booking == null) continue;
            if (activeBookingIds.add(booking.getId())) {
                int months = booking.getDurationMonths() > 0 ? booking.getDurationMonths() : 1;
                estimatedMonthly = estimatedMonthly.add(
                        booking.getTotalAmount().divide(BigDecimal.valueOf(months), 0, RoundingMode.HALF_UP));
            }
        }

        int workingDays = workingDaysInMonth(monthStart, monthEnd);
        int perVisit = workingDays > 0 ? estimatedMonthly.intValue() / workingDays : 0;

        BigDecimal tillDateEarnings = BigDecimal.ZERO;
        int totalVisits = 0;
        TreeMap<String, long[]> monthlyMap = new TreeMap<>(java.util.Collections.reverseOrder());

        for (BookingAssignment a : allAssignments) {
            if ("CANCELLED".equals(a.getAssignmentStatus())) continue;
            BookingSchedule schedule = scheduleRepository.findByIdAndDeletedFalse(a.getBookingScheduleId()).orElse(null);
            if (schedule == null) continue;
            LocalDate schedDate = schedule.getScheduledDate();
            if (schedDate.isAfter(today)) continue;
            Booking booking = bookingRepository.findByIdAndDeletedFalse(schedule.getBookingId()).orElse(null);
            if (booking == null) continue;

            int daysInThatMonth = workingDaysInMonth(schedDate.withDayOfMonth(1),
                    schedDate.withDayOfMonth(schedDate.lengthOfMonth()));
            int bookingMonths = booking.getDurationMonths() > 0 ? booking.getDurationMonths() : 1;
            BigDecimal monthlyForBooking = booking.getTotalAmount().divide(
                    BigDecimal.valueOf(bookingMonths), 2, RoundingMode.HALF_UP);
            BigDecimal visitEarning = monthlyForBooking.divide(
                    BigDecimal.valueOf(daysInThatMonth), 0, RoundingMode.HALF_UP);

            tillDateEarnings = tillDateEarnings.add(visitEarning);
            totalVisits++;

            String monthKey = schedDate.getYear() + "-" + String.format("%02d", schedDate.getMonthValue());
            monthlyMap.computeIfAbsent(monthKey, k -> new long[]{0, 0});
            monthlyMap.get(monthKey)[0] += visitEarning.longValue();
            monthlyMap.get(monthKey)[1]++;
        }

        String currentMonthKey = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
        String[] monthNames = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        List<Map<String, Object>> monthlyBreakdown = new ArrayList<>();
        for (var entry : monthlyMap.entrySet()) {
            String[] parts = entry.getKey().split("-");
            int month = Integer.parseInt(parts[1]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", monthNames[month]);
            m.put("year", Integer.parseInt(parts[0]));
            m.put("earnings", entry.getValue()[0]);
            m.put("visits", entry.getValue()[1]);
            m.put("isCurrent", entry.getKey().equals(currentMonthKey));
            monthlyBreakdown.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("estimatedMonthly", estimatedMonthly.intValue());
        result.put("perVisit", perVisit);
        result.put("workingDays", workingDays);
        result.put("tillDateEarnings", tillDateEarnings.intValue());
        result.put("totalVisits", totalVisits);
        result.put("activeBookings", activeBookingIds.size());
        result.put("month", today.getMonth().toString());
        result.put("monthlyBreakdown", monthlyBreakdown);
        return result;
    }

    private int workingDaysInMonth(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek().getValue() != 7) count++; // exclude Sundays
            d = d.plusDays(1);
        }
        return count > 0 ? count : 26;
    }
}
