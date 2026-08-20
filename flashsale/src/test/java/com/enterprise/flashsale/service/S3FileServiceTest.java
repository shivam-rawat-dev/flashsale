package com.enterprise.flashsale.service;

import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
class S3FileServiceTest {

    @Autowired
    private S3FileService s3FileService;

    @MockBean
    private S3Template s3Template; // Mocking S3 to avoid actual AWS costs/calls during build

    @Test
    void testUploadReceipt() {
        String fileName = "receipt_123.pdf";
        ByteArrayInputStream inputStream = new ByteArrayInputStream("test data".getBytes());

        s3FileService.uploadReceipt(fileName, inputStream);

        // Verify the S3 template was called correctly
        verify(s3Template).upload(eq("flashsale-order-receipts-2026"), eq(fileName), any());
    }
}