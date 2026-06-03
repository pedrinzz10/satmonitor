package br.com.fiap.satmonitor.satelite.dto;

import java.time.LocalDateTime;

public record EstatisticasResponse(
        Long sateliteId,
        String nomeSatelite,
        Double mediaValor,
        Double minValor,
        Double maxValor,
        Long totalLeituras,
        Long totalAlertas,
        Long totalCriticos,
        LocalDateTime ultimaLeitura
) {}
