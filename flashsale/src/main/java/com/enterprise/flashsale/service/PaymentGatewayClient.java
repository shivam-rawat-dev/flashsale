package com.enterprise.flashsale.service;

import com.enterprise.flashsale.dto.request.PaymentRequest;
import com.enterprise.flashsale.dto.response.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayClient {

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        // Call external payment gateway or microservice
        //return externalPaymentClient.charge(request);
        return new PaymentResponse("","");
    }

    // Fallback method must match signature + Exception parameter
    public PaymentResponse paymentFallback(PaymentRequest request, Throwable t) {
        // Log the error and return a graceful fallback response (e.g., queued for asynchronous retry)
        return new PaymentResponse("FAILED", "Payment gateway is currently experiencing high load. Your order has been queued for processing.");
    }
}