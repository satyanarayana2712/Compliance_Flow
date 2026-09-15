package com.abhiram.complianceautomationplatform.compliance.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abhiram.complianceautomationplatform.compliance.dto.ComplianceResponse;
import com.abhiram.complianceautomationplatform.compliance.dto.CreateComplianceRequest;
import com.abhiram.complianceautomationplatform.compliance.dto.UpdateComplianceRequest;
import com.abhiram.complianceautomationplatform.compliance.service.ComplianceService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/compliances")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ComplianceController {
        private final ComplianceService complianceService;

        @PostMapping
                public ComplianceResponse createCompliance(
                        @Valid @RequestBody CreateComplianceRequest request) {
                return complianceService.createCompliance(
                                request,
                                authentication);
        }

        @GetMapping
                public List<ComplianceResponse> getAllCompliances(
                        ) {
                return complianceService.getAllCompliances(
                                authentication);
        }

        @GetMapping("/{id}")
                public ComplianceResponse getComplianceById(
                        @PathVariable Long id) {
                return complianceService.getComplianceById(
                                id,
                                authentication);
        }

        @PutMapping("/{id}")
                public ComplianceResponse updateCompliance(
                        @PathVariable Long id,
                        @Valid @RequestBody UpdateComplianceRequest request) {
                return complianceService.updateCompliance(
                                id,
                                request,
                                authentication);
        }

        @DeleteMapping("/{id}")
                public String deleteCompliance(
                        @PathVariable Long id) {
                complianceService.deleteCompliance(
                                id,
                                authentication);

                return "Compliance Deleted Successfully";
        }

        @GetMapping("/my-department")
                public List<ComplianceResponse> getMyDepartmentCompliances(
                        ) {
                return complianceService
                                .getMyDepartmentCompliances(
                                                authentication);
        }

        @GetMapping("/my-compliances")
                public List<ComplianceResponse> getMyCompliances(
                        ) {
                return complianceService.getMyCompliances(
                                authentication);
        }
}
