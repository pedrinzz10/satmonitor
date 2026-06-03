package br.com.fiap.satmonitor.satelite.repository;

import br.com.fiap.satmonitor.satelite.dto.EstatisticasResponse;
import br.com.fiap.satmonitor.satelite.entity.Satelite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SateliteRepository extends JpaRepository<Satelite, Long> {

    Page<Satelite> findByMissaoId(Long missaoId, Pageable pageable);

    boolean existsByNomeAndMissaoId(String nome, Long missaoId);

    long countByMissaoId(Long missaoId);

    @Query("""
            SELECT new br.com.fiap.satmonitor.satelite.dto.EstatisticasResponse(
                s.id,
                s.nome,
                AVG(l.valor),
                MIN(l.valor),
                MAX(l.valor),
                COUNT(l.id),
                SUM(CASE WHEN l.status = br.com.fiap.satmonitor.leitura.enums.StatusLeitura.ALERTA THEN 1L ELSE 0L END),
                SUM(CASE WHEN l.status = br.com.fiap.satmonitor.leitura.enums.StatusLeitura.CRITICO THEN 1L ELSE 0L END),
                MAX(l.dataHoraLeitura)
            )
            FROM Satelite s
            JOIN s.sensores sen
            JOIN sen.leituras l
            WHERE s.id = :sateliteId
            GROUP BY s.id, s.nome
            """)
    Optional<EstatisticasResponse> buscarEstatisticas(@Param("sateliteId") Long sateliteId);
}
