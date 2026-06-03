package br.com.fiap.satmonitor.sensor.dto;

import jakarta.validation.constraints.*;

public record SensorRequest(
        @NotBlank String nome,
        @NotBlank String unidade,
        @NotNull Double limiteMin,
        @NotNull Double limiteMax,
        @NotNull @Min(0) @Max(100) Double margemAlerta,
        @NotNull Long sateliteId,
        @NotBlank String tipo,
        String unidadeEscala,
        String tipoPressao,
        String tipoRadiacao,
        String eixosMedicao
) {}
