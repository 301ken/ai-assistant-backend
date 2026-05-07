package com.ai.scheduler.dto.activity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatsResponse {

    private long totalSeconds;
    private double totalHours;
    private int sessionCount;
    private List<DailyBreakDown> dailyBreakdown;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBreakDown {
        private LocalDate date;
        private long seconds;
    }
}