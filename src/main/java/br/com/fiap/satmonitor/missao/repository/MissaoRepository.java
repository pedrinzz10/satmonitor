package br.com.fiap.satmonitor.missao.repository;

import br.com.fiap.satmonitor.missao.entity.Missao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissaoRepository extends JpaRepository<Missao, Long> {

    Page<Missao> findByMembrosOperadorId(Long operadorId, Pageable pageable);
}
