package com.contentanalytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    @Value("${app.storage.s3.bucket-name}")
    private String bucketName;

    @Value("${app.storage.s3.region:us-east-1}")
    private String region;

    @Value("${app.storage.s3.access-key}")
    private String accessKey;

    @Value("${app.storage.s3.secret-key}")
    private String secretKey;

    @Value("${app.storage.s3.endpoint:}")
    private String endpoint;

    private S3Client s3Client;

    /**
     * Initialize S3 client (lazy initialization)
     */
    private S3Client getS3Client() {
        if (s3Client == null) {
            var credentials = AwsBasicCredentials.create(accessKey, secretKey);

            var builder = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials));

            if (endpoint != null && !endpoint.isBlank()) {
                builder.endpointOverride(java.net.URI.create(endpoint));
            }

            s3Client = builder.build();
            log.info("S3 client initialized for bucket: {}", bucketName);
        }
        return s3Client;
    }

    /**
     * Upload file to S3
     */
    public String uploadFile(MultipartFile file, String userId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID() + "." + fileExtension;

            // Create S3 key with folder structure: documents/userId/YYYY/MM/DD/filename
            String dateFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String s3Key = String.format("documents/%s/%s/%s", userId, dateFolder, uniqueFilename);

            // Upload to S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .metadata(java.util.Map.of(
                            "original-filename", originalFilename,
                            "uploaded-by", userId,
                            "uploaded-at", LocalDateTime.now().toString()
                    ))
                    .build();

            getS3Client().putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            log.info("File uploaded to S3: {} for user: {}", s3Key, userId);

            return s3Key;

        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Delete file from S3
     */
    public void deleteFile(String fileKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            getS3Client().deleteObject(deleteObjectRequest);
            log.info("File deleted from S3: {}", fileKey);

        } catch (S3Exception e) {
            log.error("Error deleting file from S3: {}", fileKey, e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    /**
     * Get file URL (pre-signed or direct)
     */
    public String getFileUrl(String fileKey) {
        // For public buckets, you can return direct URL
        // For private buckets, you need to generate pre-signed URLs
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileKey);
    }

    /**
     * Check if file exists in S3
     */
    public boolean fileExists(String fileKey) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            getS3Client().headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Error checking if file exists in S3: {}", fileKey, e);
            return false;
        }
    }

    /**
     * Get file extension
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

}