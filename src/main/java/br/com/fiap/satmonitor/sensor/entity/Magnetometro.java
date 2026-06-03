package br.com.fiap.satmonitor.sensor.entity;

import br.com.fiap.satmonitor.sensor.enums.EixosMedicao;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_MAGNETOMETRO")
@DiscriminatorValue("MAGNETOMETRO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Magnetometro extends Sensor {

    @Enumerated(EnumType.STRING)
    @Column(name = "eixos_medicao")
    private EixosMedicao eixosMedicao;
}
