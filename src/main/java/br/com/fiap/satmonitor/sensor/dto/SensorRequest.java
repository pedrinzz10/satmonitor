package br.com.fiap.satmonitor.sensor.dto;

import br.com.fiap.satmonitor.sensor.enums.TipoSensor;
import jakarta.validation.constraints.*;

public record SensorRequest(
        @NotBlank String nome,
        @NotBlank String unidade,
        @NotNull Double limiteMin,
        @NotNull Double limiteMax,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double margemAlerta,
        @NotNull Long sateliteId,
        @NotNull TipoSensor tipo,
        String unidadeEscala,
        String tipoPressao,
        String tipoRadiacao,
        String eixosMedicao
) {}
