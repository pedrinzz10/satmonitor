package br.com.fiap.satmonitor.missao.entity;

import br.com.fiap.satmonitor.agencia.entity.Agencia;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "TB_MISSAO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Missao {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_missao")
    @SequenceGenerator(name = "seq_missao", sequenceName = "SEQ_MISSAO", allocationSize = 1)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nome;

    @Column(columnDefinition = "CLOB")
    private String descricao;

    @NotNull
    @Column(nullable = false)
    private LocalDate dataLancamento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMissao status;

    @Column(nullable = false)
    private String senhaMissao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agencia_id")
    private Agencia agencia;

    @Builder.Default
    @Column(name = "permite_cowork", nullable = false)
    private Boolean permitirCowork = false;

    @Column(name = "objetivo", length = 500)
    private String objetivo;

    @Column(name = "data_fim_prevista")
    private LocalDate dataFimPrevista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operador_dono_id")
    private Operador operadorDono;

    @Builder.Default
    @OneToMany(mappedBy = "missao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OperadorMissao> membros = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "missao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SolicitacaoEntrada> solicitacoes = new ArrayList<>();
}
