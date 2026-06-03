package br.com.fiap.satmonitor.auth.service;

import br.com.fiap.satmonitor.auth.dto.RegistroRequest;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.auth.repository.OperadorRepository;
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

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return operadorRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Operador não encontrado: " + login));
    }

    public Operador registrar(RegistroRequest req) {
        if (operadorRepository.findByLogin(req.login()).isPresent()) {
            throw new IllegalArgumentException("Login já está em uso");
        }
        Operador operador = Operador.builder()
                .login(req.login())
                .senha(passwordEncoder.encode(req.senha()))
                .nome(req.nome())
                .build();
        return operadorRepository.save(operador);
    }
}
