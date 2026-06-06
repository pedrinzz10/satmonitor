package br.com.fiap.satmonitor.missao.dto;

import br.com.fiap.satmonitor.missao.enums.StatusSolicitacao;

import java.time.LocalDateTime;

public record SolicitacaoResponse(
        Long id,
        Long operadorId,
        String nomeOperador,
        Long agenciaId,
        String nomeAgencia,
        StatusSolicitacao status,
        LocalDateTime dataSolicitacao
) {}
