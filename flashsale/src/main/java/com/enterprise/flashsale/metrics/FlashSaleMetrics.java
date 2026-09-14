package com.enterprise.flashsale.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class FlashSaleMetrics {

    private final Counter orderSuccessCounter;
    private final Counter orderSoldOutCounter;
    private final Counter reservationSuccessCounter;
    private final Counter reservationFailedCounter;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Timer reservationTimer;
    private final Timer checkoutTimer;

    private final AtomicLong activeReservationsGauge = new AtomicLong(0);

    public FlashSaleMetrics(MeterRegistry registry, StringRedisTemplate redisTemplate) {
        this.orderSuccessCounter = Counter.builder("flashsale.orders.success")
                .description("Total number of successfully placed flash sale orders")
                .register(registry);

        this.orderSoldOutCounter = Counter.builder("flashsale.orders.soldout")
                .description("Total number of reservations rejected due to sold-out inventory")
                .register(registry);

        this.reservationSuccessCounter = Counter.builder("flashsale.reservations.success")
                .description("Total number of successful atomic reservations")
                .register(registry);

        this.reservationFailedCounter = Counter.builder("flashsale.reservations.failed")
                .description("Total number of failed reservation attempts")
                .register(registry);

        this.paymentSuccessCounter = Counter.builder("flashsale.payments.success")
                .description("Total number of successful payment settlements")
                .register(registry);

        this.paymentFailureCounter = Counter.builder("flashsale.payments.failed")
                .description("Total number of failed payment settlements")
                .register(registry);

        this.reservationTimer = Timer.builder("flashsale.reservation.latency")
                .description("Latency distribution of atomic inventory reservation execution")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.checkoutTimer = Timer.builder("flashsale.checkout.latency")
                .description("Latency distribution of checkout cold path execution")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Dynamic Gauge tracking active in-flight reservations
        Gauge.builder("flashsale.reservations.active", activeReservationsGauge, AtomicLong::get)
                .description("Current number of active unexpired in-flight reservations")
                .register(registry);
    }

    public void incrementOrderSuccess() {
        orderSuccessCounter.increment();
    }

    public void incrementReservationSuccess() {
        reservationSuccessCounter.increment();
        activeReservationsGauge.incrementAndGet();
    }

    public void incrementReservationFailed() {
        reservationFailedCounter.increment();
    }

    public void incrementSoldOut() {
        orderSoldOutCounter.increment();
    }

    public void incrementPaymentSuccess() {
        paymentSuccessCounter.increment();
        activeReservationsGauge.decrementAndGet();
    }

    public void incrementPaymentFailure() {
        paymentFailureCounter.increment();
        activeReservationsGauge.decrementAndGet();
    }

    public Timer.Sample startReservationTimer() {
        return Timer.start();
    }

    public void stopReservationTimer(Timer.Sample sample) {
        sample.stop(reservationTimer);
    }

    public Timer.Sample startCheckoutTimer() {
        return Timer.start();
    }

    public void stopCheckoutTimer(Timer.Sample sample) {
        sample.stop(checkoutTimer);
    }
}
