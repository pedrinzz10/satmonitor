package br.com.fiap.satmonitor.auth.service;

import br.com.fiap.satmonitor.agencia.repository.AgenciaRepository;
import br.com.fiap.satmonitor.auth.dto.RegistroRequest;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.auth.repository.OperadorRepository;
import br.com.fiap.satmonitor.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperadorService implements UserDetailsService {

    private final OperadorRepository operadorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgenciaRepository agenciaRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return operadorRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Operador não encontrado: " + login));
    }

    public Operador registrar(RegistroRequest req) {
        if (operadorRepository.findByLogin(req.login()).isPresent()) {
            throw new IllegalArgumentException("Login já está em uso");
        }
        var agencia = agenciaRepository.findById(req.agenciaId())
                .orElseThrow(() -> new EntityNotFoundException("Agência não encontrada com id: " + req.agenciaId()));

        Operador operador = Operador.builder()
                .login(req.login())
                .senha(passwordEncoder.encode(req.senha()))
                .nome(req.nome())
                .agencia(agencia)
                .build();
        return operadorRepository.save(operador);
    }
}
