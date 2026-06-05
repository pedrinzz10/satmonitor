package br.com.fiap.satmonitor.leitura.dto;

import br.com.fiap.satmonitor.leitura.enums.QualidadeLeitura;
import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeituraResponse extends RepresentationModel<LeituraResponse> {

    private Long id;
    private Double valor;
    private LocalDateTime dataHoraLeitura;
    private StatusLeitura status;
    private Long sensorId;
    private String nomeSensor;
    private Long sateliteId;
    private String nomeSatelite;
    private Double latitude;
    private Double longitude;
    private QualidadeLeitura qualidade;
}
