package com.chillchat.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class OssService {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;
    
    // Switch to local storage if true
    private boolean useLocalStorage = false;

    public String uploadFile(MultipartFile file) throws IOException {
        if (useLocalStorage) {
            return uploadLocal(file);
        }
    
        // Old OSS Logic (kept for reference or fallback if needed)
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            InputStream inputStream = file.getInputStream();
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.lastIndexOf(".") != -1) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + extension;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(inputStream.available());
            metadata.setContentType(file.getContentType());
            metadata.setCacheControl("no-cache");
            metadata.setHeader("Pragma", "no-cache");
            metadata.setContentDisposition("inline;filename=" + fileName);
            metadata.setHeader("x-oss-object-acl", "public-read");

            ossClient.putObject(bucketName, fileName, inputStream, metadata);
            
            // Generate URL
            // https://bucket.endpoint/filename
            // Check if endpoint contains https or http
            String protocol = "https://";
            String rawEndpoint = endpoint;
            if (endpoint.startsWith("https://")) {
                rawEndpoint = endpoint.substring(8);
            } else if (endpoint.startsWith("http://")) {
                protocol = "http://";
                rawEndpoint = endpoint.substring(7);
            }
            
            String url = protocol + bucketName + "." + rawEndpoint + "/" + fileName;
            return url;

        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("OSS Upload failed: " + e.getMessage(), e);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private String uploadLocal(MultipartFile file) throws IOException {
        try {
            // 1. Create uploads directory if not exists
            String uploadsDir = System.getProperty("user.dir") + File.separator + "uploads";
            File dir = new File(uploadsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 2. Generate filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                 extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + extension;

            // 3. Save file
            Path path = Paths.get(uploadsDir, fileName);
            Files.write(path, file.getBytes());

            // 4. Return URL (e.g., http://localhost:8080/uploads/uuid.png)
            
            // Hardcoded base URL for simplicity or dynamic
            // return "http://localhost:8080/uploads/" + fileName;
            
            // Dynamic URL based on current request context
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            return baseUrl + "/uploads/" + fileName;
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Local Upload failed", e);
        }
    }
}
