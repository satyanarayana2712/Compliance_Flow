package com.abhiram.complianceautomationplatform.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abhiram.complianceautomationplatform.user.dto.CreateDepartmentManagerRequest;
import com.abhiram.complianceautomationplatform.user.dto.CreateEmployeeRequest;
import com.abhiram.complianceautomationplatform.user.dto.UserResponse;
import com.abhiram.complianceautomationplatform.user.service.UserService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {
        private final UserService userService;

        @PostMapping("/department-managers")
                public UserResponse createDepartmentManager(
                        @Valid @RequestBody CreateDepartmentManagerRequest request) {
                return userService
                                .createDepartmentManager(
                                                request,
                                                authentication);
        }

        @GetMapping
        public List<UserResponse> getUsers(
                        ) {
                return userService.getUsers(
                                authentication);
        }

        @GetMapping("/{id}")
        public UserResponse getUserById(
                        @PathVariable Long id) {
                return userService.getUserById(
                                id,
                                authentication);
        }

        @PostMapping("/employees")
                public UserResponse createEmployee(
                        @Valid @RequestBody CreateEmployeeRequest request) {
                return userService.createEmployee(
                                request,
                                authentication);
        }

        @GetMapping("/my-team")
                public List<UserResponse> getMyTeam(
                        ) {
                return userService.getMyTeam(
                                authentication);
        }
}
