package br.com.fiap.satmonitor.sensor.repository;

import br.com.fiap.satmonitor.sensor.entity.Sensor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Page<Sensor> findBySateliteId(Long sateliteId, Pageable pageable);

    boolean existsByNomeAndSateliteId(String nome, Long sateliteId);
}
