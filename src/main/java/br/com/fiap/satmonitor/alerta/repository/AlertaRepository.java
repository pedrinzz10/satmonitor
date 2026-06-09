package br.com.fiap.satmonitor.alerta.repository;

import br.com.fiap.satmonitor.alerta.entity.Alerta;
import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    @Query("SELECT a FROM Alerta a WHERE a.leitura.sensor.satelite.id = :sateliteId")
    Page<Alerta> findBySateliteId(@Param("sateliteId") Long sateliteId, Pageable pageable);

    @Query("SELECT a FROM Alerta a WHERE a.leitura.sensor.satelite.missao.id = :missaoId")
    Page<Alerta> findByMissaoId(@Param("missaoId") Long missaoId, Pageable pageable);

    @Query("SELECT a FROM Alerta a WHERE a.statusAlerta = :status AND a.leitura.sensor.satelite.missao.id = :missaoId")
    Page<Alerta> findByMissaoIdAndStatus(@Param("missaoId") Long missaoId,
                                         @Param("status") StatusAlerta status,
                                         Pageable pageable);

    Page<Alerta> findByStatusAlerta(StatusAlerta statusAlerta, Pageable pageable);

    @Query("SELECT a FROM Alerta a WHERE a.leitura.sensor.satelite.missao.id IN " +
           "(SELECT om.missao.id FROM OperadorMissao om WHERE om.operador.id = :operadorId)")
    Page<Alerta> findByOperadorMissoes(@Param("operadorId") Long operadorId, Pageable pageable);

    @Query("SELECT a FROM Alerta a WHERE a.statusAlerta = :status AND a.leitura.sensor.satelite.missao.id IN " +
           "(SELECT om.missao.id FROM OperadorMissao om WHERE om.operador.id = :operadorId)")
    Page<Alerta> findByStatusAlertaAndOperadorMissoes(@Param("status") StatusAlerta status,
                                                      @Param("operadorId") Long operadorId,
                                                      Pageable pageable);

    void deleteByLeituraId(Long leituraId);
}
