package br.com.fiap.satmonitor.leitura.dto;

import jakarta.validation.constraints.NotNull;

public record LeituraRequest(
        @NotNull Double valor,
        @NotNull Long sensorId
) {}
