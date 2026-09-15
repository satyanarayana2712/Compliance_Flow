package com.abhiram.complianceautomationplatform.compliance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.abhiram.complianceautomationplatform.company.entity.Company;
import com.abhiram.complianceautomationplatform.compliance.entity.Compliance;
import com.abhiram.complianceautomationplatform.department.entity.Department;

public interface ComplianceRepository extends JpaRepository<Compliance, Long> {
        List<Compliance> findByCompany(Company company);

        Optional<Compliance> findByIdAndCompany(
                        Long id,
                        Company company);

        List<Compliance> findByDepartment(
                        Department department);

        long countByCompany(Company company);

        long countByDepartment(
                        Department department);

        /**
         * A compliance is PENDING when it has no assignment that has
         * reached COMPLETED or VERIFIED yet (i.e. nobody has finished
         * their part). Compliances with zero assignments also count as
         * pending.
         */
        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.company = :company
                        AND NOT EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status IN ('COMPLETED', 'VERIFIED')
                        )
                        """)
        long countPendingByCompany(@Param("company") Company company);

        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.department = :department
                        AND NOT EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status IN ('COMPLETED', 'VERIFIED')
                        )
                        """)
        long countPendingByDepartment(@Param("department") Department department);

        /**
         * A compliance is IN_PROGRESS when at least one assignment has
         * started or finished, but not every assignment is VERIFIED yet.
         */
        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.company = :company
                        AND EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status IN ('IN_PROGRESS', 'COMPLETED', 'VERIFIED')
                        )
                        AND EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status <> 'VERIFIED'
                        )
                        """)
        long countInProgressByCompany(@Param("company") Company company);

        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.department = :department
                        AND EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status IN ('IN_PROGRESS', 'COMPLETED', 'VERIFIED')
                        )
                        AND EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status <> 'VERIFIED'
                        )
                        """)
        long countInProgressByDepartment(@Param("department") Department department);

        /**
         * A compliance is COMPLETED (awaiting verification) when it has
         * at least one assignment and every assignment is COMPLETED or
         * VERIFIED, but not all of them are VERIFIED yet.
         */
        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.company = :company
                        AND EXISTS (SELECT a FROM ComplianceAssignment a WHERE a.compliance = c)
                        AND NOT EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status NOT IN ('COMPLETED', 'VERIFIED')
                        )
                        AND EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status <> 'VERIFIED'
                        )
                        """)
        long countCompletedByCompany(@Param("company") Company company);

        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.department = :department
                        AND EXISTS (SELECT a FROM ComplianceAssignment a WHERE a.compliance = c)
                        AND NOT EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status NOT IN ('COMPLETED', 'VERIFIED')
                        )
                        AND EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status <> 'VERIFIED'
                        )
                        """)
        long countCompletedByDepartment(@Param("department") Department department);

        /**
         * A compliance is VERIFIED only when it has at least one
         * assignment and every single assignment is VERIFIED.
         */
        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.company = :company
                        AND EXISTS (SELECT a FROM ComplianceAssignment a WHERE a.compliance = c)
                        AND NOT EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status <> 'VERIFIED'
                        )
                        """)
        long countVerifiedByCompany(@Param("company") Company company);

        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.department = :department
                        AND EXISTS (SELECT a FROM ComplianceAssignment a WHERE a.compliance = c)
                        AND NOT EXISTS (
                                SELECT a FROM ComplianceAssignment a
                                WHERE a.compliance = c
                                AND a.status <> 'VERIFIED'
                        )
                        """)
        long countVerifiedByDepartment(@Param("department") Department department);

        /**
         * A compliance is OVERDUE when its due date has passed and it is
         * not yet fully VERIFIED (same "all assignments verified" rule
         * as above, just negated and combined with the date check).
         */
        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.company = :company
                        AND c.dueDate < :date
                        AND (
                                NOT EXISTS (SELECT a FROM ComplianceAssignment a WHERE a.compliance = c)
                                OR EXISTS (
                                        SELECT a FROM ComplianceAssignment a
                                        WHERE a.compliance = c
                                        AND a.status <> 'VERIFIED'
                                )
                        )
                        """)
        long countOverdueByCompany(@Param("company") Company company, @Param("date") LocalDate date);

        @Query("""
                        SELECT COUNT(c) FROM Compliance c
                        WHERE c.department = :department
                        AND c.dueDate < :date
                        AND (
                                NOT EXISTS (SELECT a FROM ComplianceAssignment a WHERE a.compliance = c)
                                OR EXISTS (
                                        SELECT a FROM ComplianceAssignment a
                                        WHERE a.compliance = c
                                        AND a.status <> 'VERIFIED'
                                )
                        )
                        """)
        long countOverdueByDepartment(@Param("department") Department department, @Param("date") LocalDate date);
}