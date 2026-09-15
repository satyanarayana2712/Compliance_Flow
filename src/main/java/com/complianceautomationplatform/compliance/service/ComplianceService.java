package com.abhiram.complianceautomationplatform.compliance.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abhiram.complianceautomationplatform.assignment.entity.ComplianceAssignment;
import com.abhiram.complianceautomationplatform.assignment.repository.ComplianceAssignmentRepository;
import com.abhiram.complianceautomationplatform.audit.annotation.Audit;
import com.abhiram.complianceautomationplatform.common.enums.ComplianceStatus;
import com.abhiram.complianceautomationplatform.compliance.dto.ComplianceResponse;
import com.abhiram.complianceautomationplatform.compliance.dto.CreateComplianceRequest;
import com.abhiram.complianceautomationplatform.compliance.dto.UpdateComplianceRequest;
import com.abhiram.complianceautomationplatform.compliance.entity.Compliance;
import com.abhiram.complianceautomationplatform.compliance.repository.ComplianceRepository;
import com.abhiram.complianceautomationplatform.department.entity.Department;
import com.abhiram.complianceautomationplatform.department.repository.DepartmentRepository;
import com.abhiram.complianceautomationplatform.document.entity.ComplianceDocument;
import com.abhiram.complianceautomationplatform.document.repository.ComplianceDocumentRepository;
import com.abhiram.complianceautomationplatform.exception.BusinessException;
import com.abhiram.complianceautomationplatform.exception.ResourceNotFoundException;
import com.abhiram.complianceautomationplatform.user.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {
        private final ComplianceRepository complianceRepository;

        private final DepartmentRepository departmentRepository;

        private final ComplianceAssignmentRepository complianceAssignmentRepository;

        private final ComplianceDocumentRepository complianceDocumentRepository;

        private final S3Client s3Client;

        @Value("${aws.s3.bucket-name}")
        private String bucketName;

        @Audit(action = "CREATE_COMPLIANCE", entityType = "COMPLIANCE", details = "Compliance created")
        @Transactional
        public ComplianceResponse createCompliance(
                        CreateComplianceRequest request) {
                

                User user = principal.getUser();

                Department department = departmentRepository
                                .findByIdAndCompany(
                                                request.getDepartmentId(),
                                                user.getCompany())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Department not found"));

                Compliance compliance = Compliance.builder()
                                .title(request.getTitle())
                                .description(request.getDescription())
                                .dueDate(request.getDueDate())
                                .frequency(request.getFrequency())
                                .company(user.getCompany())
                                .department(department)
                                .createdBy(user)
                                .createdAt(LocalDateTime.now())
                                .build();

                complianceRepository.save(compliance);

                return mapToResponse(compliance);
        }

        @Transactional(readOnly = true)
        public List<ComplianceResponse> getAllCompliances(
                        ) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                List<Compliance> compliances = complianceRepository.findByCompany(
                                currentUser.getCompany());

                return mapToResponseList(compliances);
        }

        @Transactional(readOnly = true)
        public ComplianceResponse getComplianceById(
                        Long id) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                Compliance compliance = complianceRepository.findByIdAndCompany(
                                id,
                                currentUser.getCompany())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Compliance not found"));

                return mapToResponse(compliance);
        }

        /**
         * Derives the overall status of a compliance from the status of
         * its individual assignments. A compliance can be assigned to
         * multiple employees, each progressing independently, so the
         * compliance-level status is a rollup, not a stored value:
         *
         * - No assignments, or none completed yet -> PENDING
         * - At least one assignment started/finished, but not all
         * verified -> IN_PROGRESS
         * - Every assignment is COMPLETED or VERIFIED, but at least one
         * is not yet VERIFIED -> COMPLETED (awaiting verification)
         * - Every assignment is VERIFIED -> VERIFIED
         */
        private ComplianceStatus rollupStatus(List<ComplianceStatus> assignmentStatuses) {
                if (assignmentStatuses.isEmpty()) {
                        return ComplianceStatus.PENDING;
                }

                boolean allVerified = assignmentStatuses.stream()
                                .allMatch(status -> status == ComplianceStatus.VERIFIED);

                if (allVerified) {
                        return ComplianceStatus.VERIFIED;
                }

                boolean allCompletedOrVerified = assignmentStatuses.stream()
                                .allMatch(status -> status == ComplianceStatus.COMPLETED
                                                || status == ComplianceStatus.VERIFIED);

                if (allCompletedOrVerified) {
                        return ComplianceStatus.COMPLETED;
                }

                boolean anyStarted = assignmentStatuses.stream()
                                .anyMatch(status -> status == ComplianceStatus.IN_PROGRESS
                                                || status == ComplianceStatus.COMPLETED
                                                || status == ComplianceStatus.VERIFIED);

                return anyStarted ? ComplianceStatus.IN_PROGRESS : ComplianceStatus.PENDING;
        }

        /**
         * Single-record status derivation. Fires one query for the one
         * compliance's assignments. Fine for single-record endpoints
         * (get-by-id, create, update) but must NOT be called in a loop
         * over a list -- use {@link #mapToResponseList} for lists.
         */
        private ComplianceStatus deriveStatus(Compliance compliance) {
                List<ComplianceAssignment> assignments = complianceAssignmentRepository
                                .findByCompliance(compliance);

                List<ComplianceStatus> statuses = assignments.stream()
                                .map(ComplianceAssignment::getStatus)
                                .toList();

                return rollupStatus(statuses);
        }

        private ComplianceResponse mapToResponse(Compliance compliance) {
                return mapToResponse(compliance, deriveStatus(compliance));
        }

        private ComplianceResponse mapToResponse(Compliance compliance, ComplianceStatus status) {
                return ComplianceResponse.builder()
                                .id(compliance.getId())
                                .title(compliance.getTitle())
                                .description(compliance.getDescription())
                                .dueDate(compliance.getDueDate())
                                .frequency(compliance.getFrequency())
                                .status(status)
                                .department(
                                                compliance.getDepartment() != null
                                                                ? compliance.getDepartment().getName()
                                                                : null)
                                .build();
        }

        /**
         * Maps a list of compliances to responses with their derived
         * status, fetching all assignment statuses for the whole list in
         * a single query instead of one query per compliance.
         */
        private List<ComplianceResponse> mapToResponseList(List<Compliance> compliances) {
                if (compliances.isEmpty()) {
                        return List.of();
                }

                List<Long> complianceIds = compliances.stream()
                                .map(Compliance::getId)
                                .toList();

                List<ComplianceAssignmentRepository.AssignmentStatusProjection> projections = complianceAssignmentRepository
                                .findStatusesByComplianceIdIn(complianceIds);

                Map<Long, List<ComplianceStatus>> statusesByComplianceId = projections.stream()
                                .collect(Collectors.groupingBy(
                                                ComplianceAssignmentRepository.AssignmentStatusProjection::getComplianceId,
                                                Collectors.mapping(
                                                                ComplianceAssignmentRepository.AssignmentStatusProjection::getStatus,
                                                                Collectors.toList())));

                return compliances.stream()
                                .map(compliance -> mapToResponse(
                                                compliance,
                                                rollupStatus(
                                                                statusesByComplianceId.getOrDefault(
                                                                                compliance.getId(),
                                                                                List.of()))))
                                .toList();
        }

        @Transactional
        public ComplianceResponse updateCompliance(
                        Long id,
                        UpdateComplianceRequest request) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                Compliance compliance = complianceRepository.findByIdAndCompany(
                                id,
                                currentUser.getCompany())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Compliance not found"));

                compliance.setTitle(request.getTitle());
                compliance.setDescription(request.getDescription());
                compliance.setDueDate(request.getDueDate());
                compliance.setFrequency(request.getFrequency());

                compliance = complianceRepository.save(compliance);

                return mapToResponse(compliance);
        }

        @Audit(action = "DELETE_COMPLIANCE", entityType = "COMPLIANCE", details = "Compliance deleted")
        @Transactional
        public void deleteCompliance(
                        Long id) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                Compliance compliance = complianceRepository.findByIdAndCompany(
                                id,
                                currentUser.getCompany())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Compliance not found"));

                List<ComplianceDocument> documents = complianceDocumentRepository
                                .findByCompliance(compliance);

                deleteS3Objects(documents);

                complianceDocumentRepository.deleteByCompliance(compliance);

                complianceAssignmentRepository.deleteByCompliance(compliance);

                complianceRepository.delete(compliance);
        }

        /**
         * Deletes the underlying S3 objects for a compliance's documents
         * before the document rows themselves are removed. Runs before
         * any database delete so that if S3 fails, nothing in the
         * database has changed yet -- the compliance and its documents
         * are left fully intact and the delete can be retried, rather
         * than leaving an orphaned, unreachable S3 object behind.
         *
         * A failure here does not silently continue: if S3 can't
         * confirm the delete, we abort before touching the database,
         * since deleting the document row would otherwise leak storage
         * with no remaining reference to clean it up later.
         */
        private void deleteS3Objects(List<ComplianceDocument> documents) {
                if (documents.isEmpty()) {
                        return;
                }

                List<ObjectIdentifier> keys = documents.stream()
                                .map(document -> ObjectIdentifier.builder()
                                                .key(document.getS3Key())
                                                .build())
                                .toList();

                try {
                        s3Client.deleteObjects(
                                        DeleteObjectsRequest.builder()
                                                        .bucket(bucketName)
                                                        .delete(
                                                                        Delete.builder()
                                                                                        .objects(keys)
                                                                                        .build())
                                                        .build());
                } catch (Exception ex) {
                        log.error(
                                        "Failed to delete {} S3 object(s) while deleting compliance; aborting delete so no document is removed without its file",
                                        keys.size(),
                                        ex);
                        throw new BusinessException(
                                        "Could not delete attached documents from storage. Compliance was not deleted.");
                }
        }

        @Transactional(readOnly = true)
        public List<ComplianceResponse> getMyDepartmentCompliances(
                        ) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                List<Compliance> compliances = complianceRepository
                                .findByDepartment(
                                                currentUser.getDepartment());

                return mapToResponseList(compliances);
        }

        @Transactional(readOnly = true)
        public List<ComplianceResponse> getMyCompliances(
                        ) {
                

                User currentUser = new User();
        currentUser.setId(1L);
        com.abhiram.complianceautomationplatform.role.entity.Role role = new com.abhiram.complianceautomationplatform.role.entity.Role();
        role.setName(com.abhiram.complianceautomationplatform.role.RoleConstants.OWNER);
        currentUser.setRole(role);
        com.abhiram.complianceautomationplatform.department.entity.Company company = new com.abhiram.complianceautomationplatform.department.entity.Company();
        company.setId(1L);
        currentUser.setCompany(company);

                List<Compliance> compliances = complianceAssignmentRepository
                                .findByAssignedToFetchingComplianceAndDepartment(
                                                currentUser)
                                .stream()
                                .map(ComplianceAssignment::getCompliance)
                                .toList();

                return mapToResponseList(compliances);
        }
}