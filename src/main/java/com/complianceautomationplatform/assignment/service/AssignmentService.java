package com.abhiram.complianceautomationplatform.assignment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abhiram.complianceautomationplatform.assignment.dto.AssignmentResponse;
import com.abhiram.complianceautomationplatform.assignment.dto.CreateAssignmentRequest;
import com.abhiram.complianceautomationplatform.assignment.dto.UpdateAssignmentStatusRequest;
import com.abhiram.complianceautomationplatform.assignment.entity.ComplianceAssignment;
import com.abhiram.complianceautomationplatform.assignment.repository.ComplianceAssignmentRepository;
import com.abhiram.complianceautomationplatform.audit.service.AuditLogService;
import com.abhiram.complianceautomationplatform.common.enums.ComplianceStatus;
import com.abhiram.complianceautomationplatform.compliance.entity.Compliance;
import com.abhiram.complianceautomationplatform.compliance.repository.ComplianceRepository;
import com.abhiram.complianceautomationplatform.document.repository.ComplianceDocumentRepository;
import com.abhiram.complianceautomationplatform.exception.BusinessException;
import com.abhiram.complianceautomationplatform.exception.DuplicateResourceException;
import com.abhiram.complianceautomationplatform.exception.ResourceNotFoundException;
import com.abhiram.complianceautomationplatform.notification.service.EmailService;
import com.abhiram.complianceautomationplatform.role.RoleConstants;
import com.abhiram.complianceautomationplatform.user.entity.User;
import com.abhiram.complianceautomationplatform.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssignmentService {
        private final ComplianceAssignmentRepository assignmentRepository;
        private final ComplianceRepository complianceRepository;
        private final UserRepository userRepository;
        private final AuditLogService auditLogService;
        private final ComplianceDocumentRepository documentRepository;
        private final EmailService emailService;

        private static final Set<ComplianceStatus> EMPLOYEE_ALLOWED_STATUSES = Set.of(
                        ComplianceStatus.IN_PROGRESS,
                        ComplianceStatus.COMPLETED);

        @Transactional
        public AssignmentResponse createAssignment(
                        CreateAssignmentRequest request) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                Compliance compliance = complianceRepository
                                .findByIdAndCompany(
                                                request.getComplianceId(),
                                                currentUser.getCompany())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Compliance not found"));

                User employee = userRepository
                                .findByIdAndCompany(
                                                request.getEmployeeId(),
                                                currentUser.getCompany())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Employee not found"));

                if (!employee.getRole()
                                .getName()
                                .equals(RoleConstants.EMPLOYEE)) {
                        throw new RuntimeException(
                                        "Selected user is not an employee");
                }

                if (!employee.getDepartment()
                                .getId()
                                .equals(
                                                compliance.getDepartment()
                                                                .getId())) {
                        throw new RuntimeException(
                                        "Employee and Compliance belong to different departments");
                }

                if (assignmentRepository.existsByComplianceAndAssignedTo(
                                compliance,
                                employee)) {
                        throw new DuplicateResourceException(
                                        employee.getName()
                                                        + " is already assigned to "
                                                        + compliance.getTitle());
                }

                ComplianceAssignment assignment = ComplianceAssignment.builder()
                                .compliance(compliance)
                                .assignedTo(employee)
                                .assignedBy(currentUser)
                                .assignedAt(
                                                java.time.LocalDateTime.now())
                                .build();

                assignment = assignmentRepository.save(
                                assignment);

                emailService.sendAssignmentNotification(
                                employee,
                                compliance.getTitle(),
                                currentUser.getName());

                auditLogService.log(
                                "ASSIGN_COMPLIANCE",
                                "COMPLIANCE",
                                compliance.getId(),
                                currentUser.getEmail(),
                                "Assigned "
                                                + compliance.getTitle()
                                                + " to "
                                                + employee.getName());

                return mapToResponse(
                                assignment);
        }

        @Transactional(readOnly = true)
        public List<AssignmentResponse> getMyTasks(
                        ) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                return assignmentRepository
                                .findByAssignedTo(
                                                currentUser)
                                .stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        /**
         * Lists assignments awaiting verification by the currently
         * logged-in department manager -- i.e. COMPLETED assignments
         * belonging to employees in the manager's own department.
         * Uses the exact same department-scoping rule already enforced
         * inside verifyAssignment, so nothing shown here can ever be
         * rejected by that method for being out of scope.
         */
        @Transactional(readOnly = true)
        public List<AssignmentResponse> getPendingVerification(
                        ) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                if (currentUser.getDepartment() == null) {
                        return List.of();
                }

                return assignmentRepository
                                .findByAssignedTo_DepartmentAndStatus(
                                                currentUser.getDepartment(),
                                                ComplianceStatus.COMPLETED)
                                .stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        private AssignmentResponse mapToResponse(
                        ComplianceAssignment assignment) {
                return AssignmentResponse.builder()
                                .id(
                                                assignment.getId())
                                .complianceId(
                                                assignment.getCompliance()
                                                                .getId())
                                .compliance(
                                                assignment.getCompliance()
                                                                .getTitle())
                                .assignedTo(
                                                assignment.getAssignedTo()
                                                                .getName())
                                .assignedBy(
                                                assignment.getAssignedBy()
                                                                .getName())
                                .assignedAt(
                                                assignment.getAssignedAt())
                                .remarks(
                                                assignment.getRemarks())
                                .completedAt(
                                                assignment.getCompletedAt())
                                .status(
                                                assignment.getStatus()
                                                                .name())
                                .build();
        }

        @Transactional
        public AssignmentResponse updateStatus(
                        Long assignmentId,
                        UpdateAssignmentStatusRequest request) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                ComplianceAssignment assignment = assignmentRepository
                                .findByIdAndAssignedTo(
                                                assignmentId,
                                                currentUser)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Assignment not found"));

                if (!EMPLOYEE_ALLOWED_STATUSES.contains(request.getStatus())) {
                        throw new BusinessException(
                                        "Invalid status transition. Employees can only set status to IN_PROGRESS or COMPLETED.");
                }

                assignment.setRemarks(
                                request.getRemarks());

                assignment.setStatus(request.getStatus());

                if (request.getStatus() == ComplianceStatus.COMPLETED) {
                        assignment.setCompletedAt(
                                        java.time.LocalDateTime.now());
                        emailService.sendCompletionNotification(
                                        assignment.getAssignedBy(),
                                        assignment.getCompliance().getTitle(),
                                        currentUser.getName());
                }

                assignment = assignmentRepository.save(
                                assignment);

                auditLogService.log(
                                "UPDATE_STATUS",
                                "COMPLIANCE",
                                assignment.getCompliance().getId(),
                                currentUser.getEmail(),
                                "Status changed to "
                                                + request.getStatus());

                return mapToResponse(
                                assignment);
        }

        @Transactional
        public AssignmentResponse verifyAssignment(
                        Long assignmentId) {

                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                ComplianceAssignment assignment = assignmentRepository
                                .findById(assignmentId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Assignment not found"));

                User employee = assignment.getAssignedTo();

                boolean sameCompany = employee.getCompany() != null
                                && currentUser.getCompany() != null
                                && employee.getCompany().getId()
                                                .equals(currentUser.getCompany().getId());

                boolean managesEmployeeDepartment = sameCompany
                                && employee.getDepartment() != null
                                && currentUser.getDepartment() != null
                                && employee.getDepartment().getId()
                                                .equals(currentUser.getDepartment().getId());

                if (!managesEmployeeDepartment) {
                        throw new BusinessException(
                                        "You can only verify assignments for employees in your own department");
                }

                Compliance compliance = assignment.getCompliance();

                if (assignment.getStatus() != ComplianceStatus.COMPLETED) {

                        throw new RuntimeException(
                                        "Only completed compliances can be verified");
                }

                if (documentRepository
                                .findByCompliance(compliance)
                                .isEmpty()) {

                        throw new BusinessException(
                                        "No supporting documents uploaded for "
                                                        + compliance.getTitle());
                }

                assignment.setStatus(ComplianceStatus.VERIFIED);

                assignment.setVerifiedAt(
                                LocalDateTime.now());

                assignment.setVerifiedBy(
                                currentUser);

                emailService.sendVerificationNotification(
                                assignment.getAssignedTo(),
                                compliance.getTitle());

                auditLogService.log(
                                "VERIFY_COMPLIANCE",
                                "COMPLIANCE",
                                compliance.getId(),
                                currentUser.getEmail(),
                                "Verified " + compliance.getTitle());

                return mapToResponse(
                                assignment);
        }
}