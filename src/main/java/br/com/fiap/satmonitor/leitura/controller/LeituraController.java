package br.com.fiap.satmonitor.leitura.controller;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.leitura.dto.LeituraRequest;
import br.com.fiap.satmonitor.leitura.dto.LeituraResponse;
import br.com.fiap.satmonitor.leitura.enums.StatusLeitura;
import br.com.fiap.satmonitor.leitura.service.LeituraService;
import br.com.fiap.satmonitor.satelite.controller.SateliteController;
import br.com.fiap.satmonitor.sensor.controller.SensorController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/leituras")
@RequiredArgsConstructor
@Tag(name = "Leituras", description = "Registro e consulta de leituras dos sensores")
public class LeituraController {

    private final LeituraService leituraService;

    @PostMapping
    @Operation(summary = "Registra nova leitura — público (IoT)")
    @ApiResponse(responseCode = "201")
    public ResponseEntity<LeituraResponse> criar(@RequestBody @Valid LeituraRequest req) {
        LeituraResponse response = leituraService.criar(req);
        adicionarLinks(response);
        return ResponseEntity
                .created(linkTo(methodOn(LeituraController.class).buscarPorId(response.getId())).toUri())
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Lista todas as leituras — ordenadas por data desc")
    public ResponseEntity<Page<LeituraResponse>> listar(
            @PageableDefault(size = 20, sort = "dataHoraLeitura", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<LeituraResponse> page = leituraService.listar(pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca leitura por id")
    public ResponseEntity<LeituraResponse> buscarPorId(@PathVariable Long id) {
        LeituraResponse response = leituraService.buscarPorId(id);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sensor/{sensorId}")
    @Operation(summary = "Lista leituras de um sensor — filtro opcional por status")
    public ResponseEntity<Page<LeituraResponse>> listarPorSensor(
            @PathVariable Long sensorId,
            @RequestParam(required = false) StatusLeitura status,
            @PageableDefault(size = 20, sort = "dataHoraLeitura", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<LeituraResponse> page = leituraService.listarPorSensor(sensorId, status, pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @GetMapping("/satelite/{sateliteId}")
    @Operation(summary = "Lista leituras de todos os sensores de um satélite")
    public ResponseEntity<Page<LeituraResponse>> listarPorSatelite(
            @PathVariable Long sateliteId,
            @RequestParam(required = false) StatusLeitura status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<LeituraResponse> page = leituraService.listarPorSatelite(sateliteId, status, pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui leitura — SUPERVISOR ou DONO")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        leituraService.deletar(id, operadorLogado);
        return ResponseEntity.noContent().build();
    }

    private void adicionarLinks(LeituraResponse response) {
        Long id = response.getId();
        Long sensorId = response.getSensorId();
        Long sateliteId = response.getSateliteId();

        response.add(linkTo(methodOn(LeituraController.class).buscarPorId(id)).withSelfRel());
        response.add(linkTo(methodOn(LeituraController.class).deletar(id, null)).withRel("deletar"));
        response.add(linkTo(methodOn(SensorController.class).buscarPorId(sensorId)).withRel("sensor"));
        response.add(linkTo(methodOn(SateliteController.class).buscarPorId(sateliteId)).withRel("satelite"));
    }
}
