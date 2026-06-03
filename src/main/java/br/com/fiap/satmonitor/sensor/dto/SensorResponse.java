package br.com.fiap.satmonitor.sensor.dto;

import br.com.fiap.satmonitor.sensor.enums.TipoSensor;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorResponse extends RepresentationModel<SensorResponse> {

    private Long id;
    private String nome;
    private TipoSensor tipo;
    private String unidade;
    private Double limiteMin;
    private Double limiteMax;
    private Double margemAlerta;
    private Long sateliteId;
    private String nomeSatelite;
    private String detalhe;
}
