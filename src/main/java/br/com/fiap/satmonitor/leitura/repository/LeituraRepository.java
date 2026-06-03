package br.com.fiap.satmonitor.leitura.repository;

import br.com.fiap.satmonitor.leitura.entity.LeituraSensor;
import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeituraRepository extends JpaRepository<LeituraSensor, Long> {

    Page<LeituraSensor> findBySensorId(Long sensorId, Pageable pageable);

    Page<LeituraSensor> findBySensorIdAndStatus(Long sensorId, StatusLeitura status, Pageable pageable);

    Page<LeituraSensor> findBySensorSateliteId(Long sateliteId, Pageable pageable);

    Page<LeituraSensor> findBySensorSateliteIdAndStatus(Long sateliteId, StatusLeitura status, Pageable pageable);

    @Query("SELECT l FROM LeituraSensor l ORDER BY l.dataHoraLeitura DESC")
    Page<LeituraSensor> findAllOrderByDataHoraDesc(Pageable pageable);
}
