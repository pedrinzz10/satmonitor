package br.com.fiap.satmonitor.agencia.repository;

import br.com.fiap.satmonitor.agencia.entity.Agencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgenciaRepository extends JpaRepository<Agencia, Long> {
}
