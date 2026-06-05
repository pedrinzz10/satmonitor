package br.com.fiap.satmonitor.missao.dto;

import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.enums.StatusMissao;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissaoResponse extends RepresentationModel<MissaoResponse> {

    private Long id;
    private String nome;
    private String descricao;
    private LocalDate dataLancamento;
    private StatusMissao status;
    private String roleDoOperador;
    private Integer totalMembros;
    private Integer totalSatelites;
    private Long agenciaId;
    private String nomeAgencia;
    private String objetivo;
    private LocalDate dataFimPrevista;
}
