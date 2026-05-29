package com.contentanalytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.storage.local.path:/tmp/content-analytics/uploads}")
    private String localStoragePath;

    @Value("${app.storage.local.base-url:http://localhost:8080/api/documents/files}")
    private String localBaseUrl;

    private final S3StorageService s3StorageService;

    /**
     * Save file to storage (local or S3)
     */
    public String saveFile(MultipartFile file, String userId) {
        if ("s3".equalsIgnoreCase(storageType)) {
            return s3StorageService.uploadFile(file, userId);
        } else {
            return saveFileLocally(file, userId);
        }
    }

    /**
     * Delete file from storage
     */
    public void deleteFile(String fileKey) {
        if ("s3".equalsIgnoreCase(storageType)) {
            s3StorageService.deleteFile(fileKey);
        } else {
            deleteFileLocally(fileKey);
        }
    }

    /**
     * Get file download URL
     */
    public String getFileUrl(String fileKey) {
        if ("s3".equalsIgnoreCase(storageType)) {
            return s3StorageService.getFileUrl(fileKey);
        } else {
            return localBaseUrl + "/" + fileKey;
        }
    }

    /**
     * Save file to local filesystem
     */
    private String saveFileLocally(MultipartFile file, String userId) {
        try {
            // Create directory structure: /uploads/userId/YYYY/MM/
            Path userStoragePath = Paths.get(localStoragePath, userId);
            Files.createDirectories(userStoragePath);

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID() + "." + fileExtension;

            // Full file path
            Path filePath = userStoragePath.resolve(uniqueFilename);

            // Save file
            Files.write(filePath, file.getBytes());

            log.info("File saved locally: {} for user: {}", uniqueFilename, userId);

            // Return file key
            return userId + "/" + uniqueFilename;

        } catch (IOException e) {
            log.error("Error saving file locally", e);
            throw new RuntimeException("Failed to save file", e);
        }
    }

    /**
     * Delete file from local filesystem
     */
    private void deleteFileLocally(String fileKey) {
        try {
            Path filePath = Paths.get(localStoragePath, fileKey);
            Files.deleteIfExists(filePath);
            log.info("File deleted locally: {}", fileKey);
        } catch (IOException e) {
            log.error("Error deleting file locally: {}", fileKey, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

}