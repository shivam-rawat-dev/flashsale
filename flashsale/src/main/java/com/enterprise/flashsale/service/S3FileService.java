package com.enterprise.flashsale.service;

import io.awspring.cloud.s3.S3Template;
import org.springframework.stereotype.Service;
import java.io.InputStream;

@Service
public class S3FileService {

    private final S3Template s3Template;

    public S3FileService(S3Template s3Template) {
        this.s3Template = s3Template;
    }

    public void uploadReceipt(String fileName, InputStream inputStream) {
        s3Template.upload("flashsale-order-receipts-2026", fileName, inputStream);
    }
}