package br.com.fiap.satmonitor.missao.dto;

import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembroResponse extends RepresentationModel<MembroResponse> {

    private Long operadorId;
    private String nome;
    private String login;
    private RoleMissao role;
    private LocalDateTime dataEntrada;
}
