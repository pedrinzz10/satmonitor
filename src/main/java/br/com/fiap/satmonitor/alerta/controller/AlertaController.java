package br.com.fiap.satmonitor.alerta.controller;

import br.com.fiap.satmonitor.alerta.dto.AlertaResponse;
import br.com.fiap.satmonitor.alerta.enums.StatusAlerta;
import br.com.fiap.satmonitor.alerta.service.AlertaService;
import br.com.fiap.satmonitor.auth.entity.Operador;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/alertas")
@RequiredArgsConstructor
@Tag(name = "Alertas", description = "Alertas gerados automaticamente por leituras fora dos limites")
public class AlertaController {

    private final AlertaService alertaService;

    @GetMapping
    @Operation(summary = "Lista alertas das missões do operador — filtro opcional por ?status=ATIVO|RECONHECIDO|RESOLVIDO")
    public ResponseEntity<Page<AlertaResponse>> listar(
            @RequestParam(required = false) StatusAlerta status,
            @PageableDefault(size = 20, sort = "dataAlerta") Pageable pageable,
            @AuthenticationPrincipal Operador operadorLogado) {

        Page<AlertaResponse> page = alertaService.listar(status, operadorLogado, pageable)
                .map(r -> { adicionarLinks(r); return r; });
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca alerta por id")
    public ResponseEntity<AlertaResponse> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {
        AlertaResponse response = alertaService.buscarPorId(id, operadorLogado);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/satelite/{sateliteId}")
    @Operation(summary = "Lista alertas de um satélite")
    public ResponseEntity<Page<AlertaResponse>> listarPorSatelite(
            @PathVariable Long sateliteId,
            @PageableDefault(size = 20, sort = "dataAlerta") Pageable pageable) {

        Page<AlertaResponse> page = alertaService.listarPorSatelite(sateliteId, pageable)
                .map(r -> { adicionarLinks(r); return r; });
        return ResponseEntity.ok(page);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza status do alerta — SUPERVISOR ou DONO da missão")
    public ResponseEntity<AlertaResponse> atualizarStatus(
            @PathVariable Long id,
            @RequestParam StatusAlerta novoStatus,
            @AuthenticationPrincipal Operador operadorLogado) {

        AlertaResponse response = alertaService.atualizarStatus(id, novoStatus, operadorLogado);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    private void adicionarLinks(AlertaResponse response) {
        Long id = response.getId();
        response.add(linkTo(methodOn(AlertaController.class).buscarPorId(id, null)).withSelfRel());
        response.add(linkTo(methodOn(AlertaController.class)
                .atualizarStatus(id, null, null)).withRel("atualizar-status"));
    }
}
