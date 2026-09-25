package br.com.poc.adapter.in.api.rest.dto;

import java.time.LocalDateTime;

public record UploadModeloResponse(
    String nomeBean,
    String classeInstanciada,
    String versaoHash,
    LocalDateTime dataRegistro,
    String status,
    String correlationId
) {}
