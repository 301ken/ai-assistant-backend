package com.ai.scheduler.dto.calendar;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CalendarColor {
    LAVENDER("1"),
    SAGE("2"),
    GRAPE("3"),
    FLAMINGO("4"),
    BANANA("5"),
    TANGERINE("6"),
    PEACOCK("7"),
    BLUEBERRY("8"),
    BASIL("9"),
    TOMATO("10"),
    GRAPHITE("11");

    private final String colorId;

    CalendarColor(String colorId) {
        this.colorId = colorId;
    }

    public String getColorId() {
        return colorId;
    }

    @JsonCreator
    public static CalendarColor fromValue(String value) {
        if (value == null) return null;
        // Try matching by colorId (numeric string from LLM, e.g. "11")
        for (CalendarColor c : values()) {
            if (c.colorId.equals(value)) return c;
        }
        // Try matching by name (e.g. "GRAPHITE")
        try {
            return CalendarColor.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        return null;
    }
}
