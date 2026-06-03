package br.com.fiap.satmonitor.sensor.entity;

import br.com.fiap.satmonitor.sensor.enums.UnidadeEscala;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_SENSOR_TERMICO")
@DiscriminatorValue("TERMICO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SensorTermico extends Sensor {

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_escala")
    private UnidadeEscala unidadeEscala;
}
