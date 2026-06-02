package br.com.fiap.satmonitor.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistroRequest(
        @NotBlank String login,
        @NotBlank String senha,
        @NotBlank String nome
) {}
