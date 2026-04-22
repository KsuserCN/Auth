package cn.ksuser.api.dto;

import java.time.LocalDateTime;

public class AdaptiveRiskMetricsResponse {
    private int windowHours;
    private long totalEvaluations;
    private long interceptedCount;
    private long stepUpCount;
    private long freezeCount;
    private double interceptRatePercent;
    private long falsePositiveCount;
    private double falsePositiveRatePercent;
    private long completedStepUpCount;
    private long avgVerificationLatencyMs;
    private LocalDateTime generatedAt;

    public AdaptiveRiskMetricsResponse() {
    }

    public int getWindowHours() {
        return windowHours;
    }

    public void setWindowHours(int windowHours) {
        this.windowHours = windowHours;
    }

    public long getTotalEvaluations() {
        return totalEvaluations;
    }

    public void setTotalEvaluations(long totalEvaluations) {
        this.totalEvaluations = totalEvaluations;
    }

    public long getInterceptedCount() {
        return interceptedCount;
    }

    public void setInterceptedCount(long interceptedCount) {
        this.interceptedCount = interceptedCount;
    }

    public long getStepUpCount() {
        return stepUpCount;
    }

    public void setStepUpCount(long stepUpCount) {
        this.stepUpCount = stepUpCount;
    }

    public long getFreezeCount() {
        return freezeCount;
    }

    public void setFreezeCount(long freezeCount) {
        this.freezeCount = freezeCount;
    }

    public double getInterceptRatePercent() {
        return interceptRatePercent;
    }

    public void setInterceptRatePercent(double interceptRatePercent) {
        this.interceptRatePercent = interceptRatePercent;
    }

    public long getFalsePositiveCount() {
        return falsePositiveCount;
    }

    public void setFalsePositiveCount(long falsePositiveCount) {
        this.falsePositiveCount = falsePositiveCount;
    }

    public double getFalsePositiveRatePercent() {
        return falsePositiveRatePercent;
    }

    public void setFalsePositiveRatePercent(double falsePositiveRatePercent) {
        this.falsePositiveRatePercent = falsePositiveRatePercent;
    }

    public long getCompletedStepUpCount() {
        return completedStepUpCount;
    }

    public void setCompletedStepUpCount(long completedStepUpCount) {
        this.completedStepUpCount = completedStepUpCount;
    }

    public long getAvgVerificationLatencyMs() {
        return avgVerificationLatencyMs;
    }

    public void setAvgVerificationLatencyMs(long avgVerificationLatencyMs) {
        this.avgVerificationLatencyMs = avgVerificationLatencyMs;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
