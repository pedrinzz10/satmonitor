package br.com.fiap.satmonitor.auth.repository;

import br.com.fiap.satmonitor.auth.entity.Operador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperadorRepository extends JpaRepository<Operador, Long> {
    Optional<Operador> findByLogin(String login);
}
