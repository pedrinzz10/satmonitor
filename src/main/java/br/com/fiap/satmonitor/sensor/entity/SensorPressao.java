package br.com.fiap.satmonitor.sensor.entity;

import br.com.fiap.satmonitor.sensor.enums.TipoPressao;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_SENSOR_PRESSAO")
@DiscriminatorValue("PRESSAO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SensorPressao extends Sensor {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pressao")
    private TipoPressao tipoPressao;
}
