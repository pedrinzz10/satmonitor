package br.com.fiap.satmonitor.leitura.entity;

import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import br.com.fiap.satmonitor.sensor.entity.Sensor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_LEITURA_SENSOR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeituraSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_leitura")
    @SequenceGenerator(name = "seq_leitura", sequenceName = "SEQ_LEITURA", allocationSize = 1)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Double valor;

    @Column(nullable = false)
    private LocalDateTime dataHoraLeitura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusLeitura status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;
}
