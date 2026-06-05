package br.com.fiap.satmonitor.leitura.dto;

import br.com.fiap.satmonitor.leitura.enums.QualidadeLeitura;
import jakarta.validation.constraints.NotNull;

public record LeituraRequest(
        @NotNull Double valor,
        @NotNull Long sensorId,
        Double latitude,
        Double longitude,
        QualidadeLeitura qualidade
) {}
