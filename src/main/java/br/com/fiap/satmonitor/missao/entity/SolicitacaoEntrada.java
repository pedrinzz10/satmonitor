package br.com.fiap.satmonitor.missao.entity;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.missao.enums.StatusSolicitacao;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_SOLICITACAO_ENTRADA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitacaoEntrada {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_solicitacao")
    @SequenceGenerator(name = "seq_solicitacao", sequenceName = "SEQ_SOLICITACAO", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operador_id", nullable = false)
    private Operador operador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "missao_id", nullable = false)
    private Missao missao;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

    @Builder.Default
    @Column(name = "data_solicitacao", nullable = false)
    private LocalDateTime dataSolicitacao = LocalDateTime.now();

    @Column(name = "data_resposta")
    private LocalDateTime dataResposta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "respondido_por_id")
    private Operador respondidoPor;
}
