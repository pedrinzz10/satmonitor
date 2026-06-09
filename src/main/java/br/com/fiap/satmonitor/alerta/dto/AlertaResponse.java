package br.com.fiap.satmonitor.alerta.dto;

import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaResponse extends RepresentationModel<AlertaResponse> {

    private Long id;
    private Long leituraId;
    private Double valorLeitura;
    private Long sensorId;
    private String nomeSensor;
    private Long sateliteId;
    private String nomeSatelite;
    private Long missaoId;
    private String nomeMissao;
    private String tipoAlerta;
    private String descricao;
    private LocalDateTime dataAlerta;
    private StatusAlerta statusAlerta;
}
