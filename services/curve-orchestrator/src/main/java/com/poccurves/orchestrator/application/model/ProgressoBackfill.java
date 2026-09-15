package com.poccurves.orchestrator.application.model;

public record ProgressoBackfill(int total, int concluidas, int semDado, int falhas, int pendentes) {}
