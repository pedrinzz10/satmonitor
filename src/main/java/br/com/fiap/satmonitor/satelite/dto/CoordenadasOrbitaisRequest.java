package br.com.fiap.satmonitor.satelite.dto;

import jakarta.validation.constraints.NotNull;

public record CoordenadasOrbitaisRequest(
        @NotNull Double altitudeKm,
        @NotNull Double inclinacao,
        Double longitudeNodo
) {}
