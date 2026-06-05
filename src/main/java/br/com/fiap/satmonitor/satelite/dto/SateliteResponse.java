package br.com.fiap.satmonitor.satelite.dto;

import br.com.fiap.satmonitor.satelite.enums.StatusSatelite;
import br.com.fiap.satmonitor.satelite.enums.TipoOrbita;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SateliteResponse extends RepresentationModel<SateliteResponse> {

    private Long id;
    private String nome;
    private LocalDate dataLancamento;
    private Double altitudeKm;
    private Double inclinacao;
    private Double longitudeNodo;
    private TipoOrbita tipoOrbita;
    private StatusSatelite statusSatelite;
    private Long missaoId;
    private String nomeMissao;
    private Integer totalSensores;
}
