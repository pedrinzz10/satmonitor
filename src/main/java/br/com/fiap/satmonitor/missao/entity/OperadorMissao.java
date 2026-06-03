package br.com.fiap.satmonitor.missao.entity;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "TB_OPERADOR_MISSAO")
@IdClass(OperadorMissao.OperadorMissaoId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperadorMissao {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operador_id")
    private Operador operador;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "missao_id")
    private Missao missao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleMissao role;

    @Column(nullable = false)
    private LocalDateTime dataEntrada;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class OperadorMissaoId implements Serializable {
        private Long operador;
        private Long missao;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OperadorMissaoId that)) return false;
            return Objects.equals(operador, that.operador) && Objects.equals(missao, that.missao);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operador, missao);
        }
    }
}
