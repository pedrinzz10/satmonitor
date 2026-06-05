package br.com.fiap.satmonitor.satelite.dto;

import br.com.fiap.satmonitor.satelite.enums.StatusSatelite;
import br.com.fiap.satmonitor.satelite.enums.TipoOrbita;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SateliteRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotNull LocalDate dataLancamento,
        @NotNull Long missaoId,
        @NotNull @Valid CoordenadasOrbitaisRequest coordenadas,
        TipoOrbita tipoOrbita,
        StatusSatelite statusSatelite
) {}
