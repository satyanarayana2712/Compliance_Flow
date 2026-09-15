package com.abhiram.complianceautomationplatform.assignment.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abhiram.complianceautomationplatform.assignment.entity.ComplianceAssignment;
import com.abhiram.complianceautomationplatform.common.enums.ComplianceStatus;
import com.abhiram.complianceautomationplatform.compliance.entity.Compliance;
import com.abhiram.complianceautomationplatform.department.entity.Department;
import com.abhiram.complianceautomationplatform.user.entity.User;

public interface ComplianceAssignmentRepository
                extends JpaRepository<ComplianceAssignment, Long> {
        List<ComplianceAssignment> findByAssignedTo(
                        User assignedTo);

        /**
         * Same result set as {@link #findByAssignedTo}, but eagerly
         * fetches the compliance and its department in the same query.
         * Use this instead of findByAssignedTo whenever the caller will
         * read assignment.getCompliance() (and its department) for
         * every row, to avoid one extra lazy-load query per distinct
         * compliance referenced.
         */
        @Query("""
                        SELECT a FROM ComplianceAssignment a
                        JOIN FETCH a.compliance c
                        LEFT JOIN FETCH c.department
                        WHERE a.assignedTo = :assignedTo
                        """)
        List<ComplianceAssignment> findByAssignedToFetchingComplianceAndDepartment(
                        @Param("assignedTo") User assignedTo);

        List<ComplianceAssignment> findByAssignedBy(
                        User assignedBy);

        Optional<ComplianceAssignment> findByIdAndAssignedTo(
                        Long id,
                        User assignedTo);

        long countByAssignedTo(
                        User assignedTo);

        long countByAssignedToAndStatus(
                        User assignedTo,
                        ComplianceStatus status);

        boolean existsByComplianceAndAssignedTo(
                        Compliance compliance,
                        User assignedTo);

        Optional<ComplianceAssignment> findByIdAndAssignedBy(
                        Long id,
                        User assignedBy);

        List<ComplianceAssignment> findByCompliance(
                        Compliance compliance);

        void deleteByCompliance(
                        Compliance compliance);

        List<ComplianceAssignment> findByStatusNot(
                        ComplianceStatus status);

        /**
         * Scopes the reminder scheduler's query to only the assignments
         * that could possibly need a reminder today, instead of loading
         * every non-verified assignment in the database on every run.
         *
         * The 3-day and 1-day reminders only ever fire when dueDate is
         * in the future, so we bound the upper end at dueDate <= :upTo
         * (3 days out). There is no lower bound on dueDate: an
         * assignment can be arbitrarily overdue and still need its
         * one-time overdue reminder (isOverdueReminderSent only ever
         * flips false -> true, it never resets), so we can't apply a
         * "due date >= today - N" cutoff without silently breaking
         * very-overdue reminders.
         */
        @Query("""
                        SELECT a FROM ComplianceAssignment a
                        JOIN FETCH a.compliance c
                        JOIN FETCH a.assignedTo
                        WHERE a.status <> :verifiedStatus
                        AND c.dueDate <= :upTo
                        """)
        List<ComplianceAssignment> findDueForReminderCheck(
                        @Param("verifiedStatus") ComplianceStatus verifiedStatus,
                        @Param("upTo") LocalDate upTo);

        /**
         * Batch-fetches the status of every assignment belonging to any
         * compliance in the given set of IDs, in a single round trip.
         * Selects only the compliance ID and status (not the full
         * assignment graph) so callers can group results in memory
         * without touching any lazy associations.
         *
         * Used to compute the per-compliance status rollup for list
         * endpoints without an N+1 query per compliance.
         */
        @Query("""
                        SELECT a.compliance.id AS complianceId, a.status AS status
                        FROM ComplianceAssignment a
                        WHERE a.compliance.id IN :complianceIds
                        """)
        List<AssignmentStatusProjection> findStatusesByComplianceIdIn(
                        @Param("complianceIds") Collection<Long> complianceIds);

        interface AssignmentStatusProjection {
                Long getComplianceId();

                ComplianceStatus getStatus();
        }

        /**
         * Finds all assignments in a given department that are
         * COMPLETED and therefore awaiting a department manager's
         * verification. Mirrors the exact scoping rule already
         * enforced inside AssignmentService.verifyAssignment
         * (assignedTo.department == currentUser.department), so a
         * manager only ever sees assignments they're actually allowed
         * to verify.
         */
        @Query("""
                        SELECT a FROM ComplianceAssignment a
                        JOIN FETCH a.compliance c
                        JOIN FETCH a.assignedTo e
                        WHERE e.department = :department
                        AND a.status = :status
                        """)
        List<ComplianceAssignment> findByAssignedTo_DepartmentAndStatus(
                        @Param("department") Department department,
                        @Param("status") ComplianceStatus status);
}