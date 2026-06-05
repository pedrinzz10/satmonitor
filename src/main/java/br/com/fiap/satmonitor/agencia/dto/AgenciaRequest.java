package br.com.fiap.satmonitor.agencia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgenciaRequest(
        @NotBlank String nome,
        @NotBlank @Size(min = 2, max = 2) String siglaPais,
        String tipoAgencia
) {}
