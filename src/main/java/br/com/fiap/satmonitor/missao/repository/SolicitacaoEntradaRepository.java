package br.com.fiap.satmonitor.missao.repository;

import br.com.fiap.satmonitor.missao.entity.SolicitacaoEntrada;
import br.com.fiap.satmonitor.missao.enums.StatusSolicitacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoEntradaRepository extends JpaRepository<SolicitacaoEntrada, Long> {

    Page<SolicitacaoEntrada> findByMissaoIdAndStatus(Long missaoId, StatusSolicitacao status, Pageable pageable);

    boolean existsByMissaoIdAndOperadorIdAndStatus(Long missaoId, Long operadorId, StatusSolicitacao status);
}
