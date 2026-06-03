package br.com.fiap.satmonitor.satelite.entity;

import br.com.fiap.satmonitor.missao.entity.Missao;
import br.com.fiap.satmonitor.sensor.entity.Sensor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "TB_SATELITE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Satelite {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_satelite")
    @SequenceGenerator(name = "seq_satelite", sequenceName = "SEQ_SATELITE", allocationSize = 1)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nome;

    @NotNull
    @Column(nullable = false)
    private LocalDate dataLancamento;

    @Embedded
    private CoordenadasOrbitais coordenadas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "missao_id", nullable = false)
    private Missao missao;

    @Builder.Default
    @OneToMany(mappedBy = "satelite", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Sensor> sensores = new ArrayList<>();
}
