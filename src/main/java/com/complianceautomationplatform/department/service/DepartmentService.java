package com.abhiram.complianceautomationplatform.department.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.abhiram.complianceautomationplatform.audit.annotation.Audit;
import com.abhiram.complianceautomationplatform.department.dto.CreateDepartmentRequest;
import com.abhiram.complianceautomationplatform.department.dto.DepartmentResponse;
import com.abhiram.complianceautomationplatform.department.entity.Department;
import com.abhiram.complianceautomationplatform.department.repository.DepartmentRepository;
import com.abhiram.complianceautomationplatform.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    @Audit(
        action = "CREATE_DEPARTMENT",
        entityType = "DEPARTMENT",
        details = "Department created")
    public DepartmentResponse createDepartment(
            CreateDepartmentRequest request) {
        

        User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

        Department department = Department.builder()
                .name(request.getName())
                .company(currentUser.getCompany())
                .createdAt(LocalDateTime.now())
                .build();

        department = departmentRepository.save(department);

        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .build();
    }

    public List<DepartmentResponse> getDepartments(
            ) {
        

        User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

        return departmentRepository
                .findByCompany(currentUser.getCompany())
                .stream()
                .map(department -> DepartmentResponse.builder()
                        .id(department.getId())
                        .name(department.getName())
                        .build())
                .collect(Collectors.toList());
    }
}
