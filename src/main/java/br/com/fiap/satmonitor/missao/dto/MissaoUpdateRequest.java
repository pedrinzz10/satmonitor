package br.com.fiap.satmonitor.missao.dto;

import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MissaoUpdateRequest(
        @NotBlank String nome,
        String descricao,
        @NotNull LocalDate dataLancamento,
        @NotNull StatusMissao status,
        Long agenciaId,
        String objetivo,
        LocalDate dataFimPrevista
) {}
