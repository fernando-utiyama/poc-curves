package com.poccurves.orchestrator.domain;

public record ProgressoBackfill(int total, int concluidas, int semDado, int falhas, int pendentes) {}
