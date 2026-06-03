package br.com.fiap.satmonitor.sensor.entity;

import br.com.fiap.satmonitor.leitura.entity.LeituraSensor;
import br.com.fiap.satmonitor.satelite.entity.Satelite;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "TB_SENSOR")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_sensor", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_sensor")
    @SequenceGenerator(name = "seq_sensor", sequenceName = "SEQ_SENSOR", allocationSize = 1)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nome;

    @NotBlank
    @Column(nullable = false)
    private String unidade;

    @NotNull
    @Column(nullable = false)
    private Double limiteMin;

    @NotNull
    @Column(nullable = false)
    private Double limiteMax;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(nullable = false)
    private Double margemAlerta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "satelite_id", nullable = false)
    private Satelite satelite;

    @Builder.Default
    @OneToMany(mappedBy = "sensor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeituraSensor> leituras = new ArrayList<>();
}
