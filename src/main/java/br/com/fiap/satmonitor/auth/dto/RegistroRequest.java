package br.com.fiap.satmonitor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistroRequest(
        @NotBlank String login,
        @NotBlank String senha,
        @NotBlank String nome,
        @NotNull Long agenciaId
) {}
