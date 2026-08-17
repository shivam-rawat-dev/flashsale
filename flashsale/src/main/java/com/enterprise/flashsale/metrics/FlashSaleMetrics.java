package com.enterprise.flashsale.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class FlashSaleMetrics {

    private final Counter orderSuccessCounter;
    private final Counter orderSoldOutCounter;
    private final Timer reservationTimer;

    public FlashSaleMetrics(MeterRegistry registry) {
        this.orderSuccessCounter = Counter.builder("flashsale.orders.success")
                .description("Number of successfully placed flash sale orders")
                .register(registry);

        this.orderSoldOutCounter = Counter.builder("flashsale.orders.soldout")
                .description("Number of orders rejected due to sold-out inventory")
                .register(registry);

        this.reservationTimer = Timer.builder("flashsale.reservation.latency")
                .description("Latency of atomic inventory reservation execution")
                .register(registry);
    }

    public void incrementSuccess() {
        orderSuccessCounter.increment();
    }

    public void incrementSoldOut() {
        orderSoldOutCounter.increment();
    }

    public Timer.Sample startReservationTimer() {
        return Timer.start();
    }

    public void stopReservationTimer(Timer.Sample sample) {
        sample.stop(reservationTimer);
    }
}