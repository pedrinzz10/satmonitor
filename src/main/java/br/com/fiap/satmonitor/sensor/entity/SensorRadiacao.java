package br.com.fiap.satmonitor.sensor.entity;

import br.com.fiap.satmonitor.sensor.enums.TipoRadiacao;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_SENSOR_RADIACAO")
@DiscriminatorValue("RADIACAO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SensorRadiacao extends Sensor {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_radiacao")
    private TipoRadiacao tipoRadiacao;
}
