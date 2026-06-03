package br.com.fiap.satmonitor.missao.dto;

import jakarta.validation.constraints.NotBlank;

public record EntrarMissaoRequest(
        @NotBlank String senha
) {}
