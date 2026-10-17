package dev.licaza.itacatl.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BusinessHoursDTO(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}
