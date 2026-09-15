package com.abhiram.complianceautomationplatform.dashboard.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abhiram.complianceautomationplatform.assignment.repository.ComplianceAssignmentRepository;
import com.abhiram.complianceautomationplatform.common.enums.ComplianceStatus;
import com.abhiram.complianceautomationplatform.compliance.repository.ComplianceRepository;
import com.abhiram.complianceautomationplatform.dashboard.dto.EmployeeDashboardResponse;
import com.abhiram.complianceautomationplatform.dashboard.dto.ManagerDashboardResponse;
import com.abhiram.complianceautomationplatform.dashboard.dto.OwnerDashboardResponse;
import com.abhiram.complianceautomationplatform.dashboard.dto.TeamMemberPerformanceResponse;
import com.abhiram.complianceautomationplatform.department.repository.DepartmentRepository;
import com.abhiram.complianceautomationplatform.exception.ResourceNotFoundException;
import com.abhiram.complianceautomationplatform.role.RoleConstants;
import com.abhiram.complianceautomationplatform.role.entity.Role;
import com.abhiram.complianceautomationplatform.role.repository.RoleRepository;
import com.abhiram.complianceautomationplatform.user.entity.User;
import com.abhiram.complianceautomationplatform.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

        private final DepartmentRepository departmentRepository;

        private final UserRepository userRepository;

        private final ComplianceRepository complianceRepository;

        private final RoleRepository roleRepository;

        private final ComplianceAssignmentRepository assignmentRepository;

        @Transactional(readOnly = true)
        public OwnerDashboardResponse getOwnerDashboard(
                        ) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                return OwnerDashboardResponse.builder()
                                .totalDepartments(
                                                departmentRepository.countByCompany(
                                                                currentUser.getCompany()))
                                .totalUsers(
                                                userRepository.countByCompany(
                                                                currentUser.getCompany()))
                                .totalCompliances(
                                                complianceRepository.countByCompany(
                                                                currentUser.getCompany()))
                                .pendingCompliances(
                                                complianceRepository
                                                                .countPendingByCompany(
                                                                                currentUser.getCompany()))
                                .inProgressCompliances(
                                                complianceRepository
                                                                .countInProgressByCompany(
                                                                                currentUser.getCompany()))
                                .completedCompliances(
                                                complianceRepository
                                                                .countCompletedByCompany(
                                                                                currentUser.getCompany()))
                                .verifiedCompliances(
                                                complianceRepository
                                                                .countVerifiedByCompany(
                                                                                currentUser.getCompany()))

                                .overdueCompliances(
                                                complianceRepository
                                                                .countOverdueByCompany(
                                                                                currentUser.getCompany(),
                                                                                LocalDate.now()))
                                .build();
        }

        @Transactional(readOnly = true)
        public ManagerDashboardResponse getManagerDashboard(
                        ) {
                

                User currentUser = userRepository
                                .findById(
                                                principal.getUser().getId())
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "User not found"));
                Role employeeRole = roleRepository
                                .findByName(
                                                RoleConstants.EMPLOYEE)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Role not found"));

                return ManagerDashboardResponse.builder()
                                .department(
                                                currentUser.getDepartment()
                                                                .getName())
                                .totalEmployees(
                                                userRepository.countByDepartmentAndRole(
                                                                currentUser.getDepartment(),
                                                                employeeRole))
                                .totalCompliances(
                                                complianceRepository.countByDepartment(
                                                                currentUser.getDepartment()))
                                .pendingCompliances(
                                                complianceRepository
                                                                .countPendingByDepartment(
                                                                                currentUser.getDepartment()))
                                .inProgressCompliances(
                                                complianceRepository
                                                                .countInProgressByDepartment(
                                                                                currentUser.getDepartment()))
                                .completedCompliances(
                                                complianceRepository
                                                                .countCompletedByDepartment(
                                                                                currentUser.getDepartment()))
                                .verifiedCompliances(
                                                complianceRepository
                                                                .countVerifiedByDepartment(
                                                                                currentUser.getDepartment()))

                                .overdueCompliances(
                                                complianceRepository
                                                                .countOverdueByDepartment(
                                                                                currentUser.getDepartment(),
                                                                                LocalDate.now()))
                                .build();
        }

        @Transactional(readOnly = true)
        public List<TeamMemberPerformanceResponse> getTeamPerformance(
                        ) {
                

                User currentUser = userRepository
                                .findByIdWithDepartment(
                                                principal.getUser().getId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found"));

                Role employeeRole = roleRepository
                                .findByName(
                                                RoleConstants.EMPLOYEE)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Role not found"));

                List<User> employees = userRepository
                                .findByDepartmentAndRole(
                                                currentUser.getDepartment(),
                                                employeeRole);

                return employees.stream()
                                .map(employee -> TeamMemberPerformanceResponse
                                                .builder()
                                                .userId(employee.getId())
                                                .employeeName(
                                                                employee.getName())
                                                .assignedTasks(
                                                                assignmentRepository
                                                                                .countByAssignedTo(
                                                                                                employee))
                                                .completedTasks(
                                                                assignmentRepository
                                                                                .countByAssignedToAndStatus(
                                                                                                employee,
                                                                                                ComplianceStatus.COMPLETED))
                                                .build())
                                .toList();
        }

        @Transactional(readOnly = true)
        public EmployeeDashboardResponse getEmployeeDashboard(
                        ) {

                

                User currentUser = userRepository.findById(
                                principal.getUser().getId())
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "User not found"));

                return EmployeeDashboardResponse.builder()
                                .assignedTasks(
                                                assignmentRepository.countByAssignedTo(
                                                                currentUser))
                                .pendingTasks(
                                                assignmentRepository
                                                                .countByAssignedToAndStatus(
                                                                                currentUser,
                                                                                ComplianceStatus.PENDING))
                                .inProgressTasks(
                                                assignmentRepository
                                                                .countByAssignedToAndStatus(
                                                                                currentUser,
                                                                                ComplianceStatus.IN_PROGRESS))
                                .completedTasks(
                                                assignmentRepository
                                                                .countByAssignedToAndStatus(
                                                                                currentUser,
                                                                                ComplianceStatus.COMPLETED))
                                .verifiedTasks(
                                                assignmentRepository
                                                                .countByAssignedToAndStatus(
                                                                                currentUser,
                                                                                ComplianceStatus.VERIFIED))
                                .build();
        }

}