package com.crm.dto;

/** Lead conversion figures for one acquisition source (or the overall total). */
public record ConversionStat(long total, long converted, double rate) {
}
