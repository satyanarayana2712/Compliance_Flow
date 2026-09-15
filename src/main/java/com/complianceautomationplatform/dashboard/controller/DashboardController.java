package com.abhiram.complianceautomationplatform.dashboard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abhiram.complianceautomationplatform.dashboard.dto.EmployeeDashboardResponse;
import com.abhiram.complianceautomationplatform.dashboard.dto.ManagerDashboardResponse;
import com.abhiram.complianceautomationplatform.dashboard.dto.OwnerDashboardResponse;
import com.abhiram.complianceautomationplatform.dashboard.dto.TeamMemberPerformanceResponse;
import com.abhiram.complianceautomationplatform.dashboard.service.DashboardService;
import com.abhiram.complianceautomationplatform.dashboard.dto.EmployeeDashboardResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
        private final DashboardService dashboardService;

        @GetMapping("/owner")
                public OwnerDashboardResponse getOwnerDashboard(
                        ) {
                return dashboardService.getOwnerDashboard(
                                authentication);
        }

        @GetMapping("/manager")
                public ManagerDashboardResponse getManagerDashboard(
                        ) {
                return dashboardService
                                .getManagerDashboard(
                                                authentication);
        }

        @GetMapping("/manager/team")
                public List<TeamMemberPerformanceResponse> getTeamPerformance(
                        ) {
                return dashboardService
                                .getTeamPerformance(
                                                authentication);
        }

        @GetMapping("/employee")
                public EmployeeDashboardResponse getEmployeeDashboard(
                        ) {

                return dashboardService.getEmployeeDashboard(
                                authentication);
        }
}