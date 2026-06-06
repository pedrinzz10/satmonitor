package br.com.fiap.satmonitor.missao.dto;

import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MissaoRequest(
        @NotBlank @Size(max = 255) String nome,
        String descricao,
        @NotNull LocalDate dataLancamento,
        @NotNull StatusMissao status,
        @NotBlank @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres") String senhaMissao,
        Long agenciaId,
        String objetivo,
        LocalDate dataFimPrevista,
        Boolean permitirCowork
) {}
