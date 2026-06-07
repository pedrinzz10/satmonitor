package br.com.fiap.satmonitor.agencia.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "TB_AGENCIA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agencia {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_agencia")
    @SequenceGenerator(name = "seq_agencia", sequenceName = "SEQ_AGENCIA", allocationSize = 1)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String nome;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(name = "sigla_pais", nullable = false, length = 2)
    private String siglaPais;

    @Column(name = "tipo_agencia", length = 50)
    private String tipoAgencia;

    @Builder.Default
    @Column(name = "data_cadastro", nullable = false)
    private LocalDate dataCadastro = LocalDate.now();
}
