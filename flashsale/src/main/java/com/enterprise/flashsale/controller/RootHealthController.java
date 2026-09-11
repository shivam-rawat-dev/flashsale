package com.enterprise.flashsale.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health & Root", description = "Endpoints for ALB and container health monitoring")
public class RootHealthController {

    @GetMapping("/")
    @Operation(summary = "Root ALB Health Check", description = "Returns HTTP 200 for ALB Target Groups configured with root '/' path")
    public ResponseEntity<Map<String, String>> rootHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "flashsale-backend"
        ));
    }
}
