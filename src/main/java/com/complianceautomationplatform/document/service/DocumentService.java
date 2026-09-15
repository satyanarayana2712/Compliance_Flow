package com.abhiram.complianceautomationplatform.document.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.abhiram.complianceautomationplatform.assignment.repository.ComplianceAssignmentRepository;
import com.abhiram.complianceautomationplatform.compliance.entity.Compliance;
import com.abhiram.complianceautomationplatform.compliance.repository.ComplianceRepository;
import com.abhiram.complianceautomationplatform.document.dto.DocumentResponse;
import com.abhiram.complianceautomationplatform.document.dto.DownloadUrlResponse;
import com.abhiram.complianceautomationplatform.document.entity.ComplianceDocument;
import com.abhiram.complianceautomationplatform.document.repository.ComplianceDocumentRepository;
import com.abhiram.complianceautomationplatform.exception.BusinessException;
import com.abhiram.complianceautomationplatform.exception.ResourceNotFoundException;
import com.abhiram.complianceautomationplatform.user.entity.User;
import com.abhiram.complianceautomationplatform.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ComplianceDocumentRepository documentRepository;
    private final ComplianceRepository complianceRepository;
    private final ComplianceAssignmentRepository complianceAssignmentRepository;
    private final UserRepository userRepository;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String UPLOAD_DIR = "uploads";

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    @Transactional
    public DocumentResponse uploadDocument(Long complianceId, MultipartFile file, Long userId) throws IOException {

        if (file.isEmpty()) {
            throw new BusinessException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Invalid content type");
        }

        String originalName = file.getOriginalFilename();
        String safeName = originalName != null ? originalName.replaceAll("[^a-zA-Z0-9._-]", "_") : "document";
        String lowerFileName = safeName.toLowerCase();

        if (!(lowerFileName.endsWith(".pdf") || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".jpg")
                || lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".doc")
                || lowerFileName.endsWith(".docx"))) {
            throw new BusinessException("Invalid file extension");
        }

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Compliance compliance = complianceRepository.findById(complianceId)
                .orElseThrow(() -> new ResourceNotFoundException("Compliance not found"));

        String uniqueFileName = UUID.randomUUID() + "-" + safeName;
        Path uploadPath = Paths.get(UPLOAD_DIR);
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath);

        String documentUrl = filePath.toString();

        ComplianceDocument document = ComplianceDocument.builder()
                .fileName(safeName)
                .fileType(contentType)
                .fileSize(file.getSize())
                .s3Key(uniqueFileName) // Using this field for local file name to avoid DB schema changes
                .documentUrl(documentUrl)
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(currentUser)
                .compliance(compliance)
                .build();

        document = documentRepository.save(document);

        return DocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .documentUrl(document.getDocumentUrl())
                .uploadedBy(currentUser.getName())
                .uploadedAt(document.getUploadedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByCompliance(Long complianceId, Long userId) {

        Compliance compliance = complianceRepository.findById(complianceId)
                .orElseThrow(() -> new ResourceNotFoundException("Compliance not found"));

        return documentRepository.findByCompliance(compliance).stream()
                .map(document -> DocumentResponse.builder()
                        .id(document.getId())
                        .fileName(document.getFileName())
                        .fileType(document.getFileType())
                        .fileSize(document.getFileSize())
                        .documentUrl(document.getDocumentUrl())
                        .uploadedBy(document.getUploadedBy().getName())
                        .uploadedAt(document.getUploadedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse generateDownloadUrl(Long documentId, Long userId) {

        ComplianceDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        return DownloadUrlResponse.builder()
                .downloadUrl(document.getDocumentUrl()) // Just returning local path for simplicity
                .build();
    }
}
