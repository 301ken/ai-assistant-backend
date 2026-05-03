package com.ai.scheduler.dto.calendar;

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
}
