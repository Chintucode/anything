package com.chintu.anything.parser;

/** The header block at the top of a plan (between the two --- lines). */
public record PlanHeader(int formatVersion, String title, String category, int weeks) {
}
