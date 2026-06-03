package br.com.fiap.satmonitor.sensor.controller;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.satelite.controller.SateliteController;
import br.com.fiap.satmonitor.sensor.dto.SensorRequest;
import br.com.fiap.satmonitor.sensor.dto.SensorResponse;
import br.com.fiap.satmonitor.sensor.service.SensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/sensores")
@RequiredArgsConstructor
@Tag(name = "Sensores", description = "Gerenciamento de sensores dos satélites")
public class SensorController {

    private final SensorService sensorService;

    @PostMapping
    @Operation(summary = "Cria novo sensor — SUPERVISOR ou DONO")
    @ApiResponse(responseCode = "201")
    public ResponseEntity<SensorResponse> criar(
            @RequestBody @Valid SensorRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        SensorResponse response = sensorService.criar(req, operadorLogado);
        adicionarLinks(response);

        return ResponseEntity
                .created(linkTo(methodOn(SensorController.class).buscarPorId(response.getId())).toUri())
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Lista todos os sensores")
    public ResponseEntity<Page<SensorResponse>> listar(
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        Page<SensorResponse> page = sensorService.listar(pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca sensor por id")
    public ResponseEntity<SensorResponse> buscarPorId(@PathVariable Long id) {
        SensorResponse response = sensorService.buscarPorId(id);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/satelite/{sateliteId}")
    @Operation(summary = "Lista sensores de um satélite")
    public ResponseEntity<Page<SensorResponse>> listarPorSatelite(
            @PathVariable Long sateliteId,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        Page<SensorResponse> page = sensorService.listarPorSatelite(sateliteId, pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza sensor — SUPERVISOR ou DONO")
    public ResponseEntity<SensorResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid SensorRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        SensorResponse response = sensorService.atualizar(id, req, operadorLogado);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui sensor — apenas DONO")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        sensorService.deletar(id, operadorLogado);
        return ResponseEntity.noContent().build();
    }

    private void adicionarLinks(SensorResponse response) {
        Long id = response.getId();
        Long sateliteId = response.getSateliteId();

        response.add(linkTo(methodOn(SensorController.class).buscarPorId(id)).withSelfRel());
        response.add(linkTo(methodOn(SensorController.class).atualizar(id, null, null)).withRel("atualizar"));
        response.add(linkTo(methodOn(SensorController.class).deletar(id, null)).withRel("deletar"));
        response.add(Link.of("/leituras/sensor/" + id, "leituras"));
        response.add(linkTo(methodOn(SateliteController.class).buscarPorId(sateliteId)).withRel("satelite"));
    }
}
