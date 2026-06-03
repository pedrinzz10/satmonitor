package br.com.fiap.satmonitor.missao.repository;

import br.com.fiap.satmonitor.missao.entity.OperadorMissao;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperadorMissaoRepository extends JpaRepository<OperadorMissao, OperadorMissao.OperadorMissaoId> {

    Optional<OperadorMissao> findByMissaoIdAndOperadorId(Long missaoId, Long operadorId);

    List<OperadorMissao> findByMissaoId(Long missaoId);

    boolean existsByMissaoIdAndOperadorId(Long missaoId, Long operadorId);

    long countByMissaoIdAndRole(Long missaoId, RoleMissao role);
}
