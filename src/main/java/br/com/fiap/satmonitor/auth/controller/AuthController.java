package br.com.fiap.satmonitor.auth.controller;

import br.com.fiap.satmonitor.auth.dto.LoginRequest;
import br.com.fiap.satmonitor.auth.dto.RegistroRequest;
import br.com.fiap.satmonitor.auth.dto.TokenResponse;
import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.auth.service.OperadorService;
import br.com.fiap.satmonitor.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Autenticação e registro de operadores")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final OperadorService operadorService;

    @PostMapping("/login")
    @Operation(summary = "Autenticar operador", description = "Valida credenciais e retorna token JWT válido por 8 horas")
    @ApiResponse(responseCode = "200", description = "Token gerado com sucesso")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        var authToken = new UsernamePasswordAuthenticationToken(request.login(), request.senha());
        var auth = authenticationManager.authenticate(authToken);
        var token = tokenService.gerarToken((Operador) auth.getPrincipal());
        return ResponseEntity.ok(new TokenResponse(token));
    }

    @PostMapping("/registrar")
    @Operation(summary = "Registrar operador", description = "Cria um novo operador no sistema")
    @ApiResponse(responseCode = "201", description = "Operador registrado com sucesso")
    @ApiResponse(responseCode = "400", description = "Login já existe ou campos inválidos")
    public ResponseEntity<Void> registrar(@RequestBody @Valid RegistroRequest request) {
        operadorService.registrar(request);
        return ResponseEntity.status(201).build();
    }
}
