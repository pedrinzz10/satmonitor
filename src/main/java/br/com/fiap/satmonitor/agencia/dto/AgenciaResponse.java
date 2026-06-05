package br.com.fiap.satmonitor.agencia.dto;

import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgenciaResponse extends RepresentationModel<AgenciaResponse> {

    private Long id;
    private String nome;
    private String siglaPais;
    private String tipoAgencia;
    private LocalDate dataCadastro;
}
