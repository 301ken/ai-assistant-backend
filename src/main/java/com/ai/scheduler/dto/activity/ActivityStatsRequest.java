package com.ai.scheduler.dto.activity;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatsRequest {

    private LocalDate from;
    private LocalDate to;
    
}