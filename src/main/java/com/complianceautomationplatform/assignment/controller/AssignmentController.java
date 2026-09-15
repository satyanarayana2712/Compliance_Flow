package com.abhiram.complianceautomationplatform.assignment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abhiram.complianceautomationplatform.assignment.dto.AssignmentResponse;
import com.abhiram.complianceautomationplatform.assignment.dto.CreateAssignmentRequest;
import com.abhiram.complianceautomationplatform.assignment.dto.UpdateAssignmentStatusRequest;
import com.abhiram.complianceautomationplatform.assignment.service.AssignmentService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AssignmentController {
        private final AssignmentService assignmentService;

        @PostMapping
                public AssignmentResponse createAssignment(
                        @Valid @RequestBody CreateAssignmentRequest request) {
                return assignmentService
                                .createAssignment(
                                                request,
                                                authentication);
        }

        @GetMapping("/my-tasks")
                public List<AssignmentResponse> getMyTasks(
                        ) {
                return assignmentService
                                .getMyTasks(
                                                authentication);
        }

        @GetMapping("/pending-verification")
                public List<AssignmentResponse> getPendingVerification(
                        ) {
                return assignmentService
                                .getPendingVerification(
                                                authentication);
        }

        @PatchMapping("/{id}/status")
                public AssignmentResponse updateStatus(
                        @PathVariable Long id,

                        @Valid @RequestBody UpdateAssignmentStatusRequest request) {
                return assignmentService
                                .updateStatus(
                                                id,
                                                request,
                                                authentication);
        }

        @PutMapping("/{assignmentId}/verify")
                public AssignmentResponse verifyAssignment(
                        @PathVariable Long assignmentId) {

                return assignmentService
                                .verifyAssignment(
                                                assignmentId,
                                                authentication);
        }

        

}
