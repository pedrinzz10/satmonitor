package br.com.fiap.satmonitor.alerta.entity;

import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import br.com.fiap.satmonitor.leitura.entity.LeituraSensor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_ALERTA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_alerta")
    @SequenceGenerator(name = "seq_alerta", sequenceName = "SEQ_ALERTA", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leitura_id", nullable = false)
    private LeituraSensor leitura;

    @Column(name = "tipo_alerta", nullable = false, length = 20)
    private String tipoAlerta;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Builder.Default
    @Column(name = "data_alerta", nullable = false)
    private LocalDateTime dataAlerta = LocalDateTime.now();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status_alerta", nullable = false, length = 20)
    private StatusAlerta statusAlerta = StatusAlerta.ATIVO;
}
