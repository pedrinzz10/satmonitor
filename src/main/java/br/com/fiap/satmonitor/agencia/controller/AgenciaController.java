package br.com.fiap.satmonitor.agencia.controller;

import br.com.fiap.satmonitor.agencia.dto.AgenciaRequest;
import br.com.fiap.satmonitor.agencia.dto.AgenciaResponse;
import br.com.fiap.satmonitor.agencia.service.AgenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/agencias")
@RequiredArgsConstructor
@Tag(name = "Agências", description = "Agências espaciais vinculadas às missões")
public class AgenciaController {

    private final AgenciaService agenciaService;

    @PostMapping
    @Operation(summary = "Cria nova agência espacial")
    public ResponseEntity<AgenciaResponse> criar(@RequestBody @Valid AgenciaRequest req) {
        AgenciaResponse response = agenciaService.criar(req);
        adicionarLinks(response);
        return ResponseEntity
                .created(linkTo(methodOn(AgenciaController.class).buscarPorId(response.getId())).toUri())
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Lista todas as agências")
    public ResponseEntity<Page<AgenciaResponse>> listar(
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        Page<AgenciaResponse> page = agenciaService.listar(pageable)
                .map(r -> { adicionarLinks(r); return r; });
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca agência por id")
    public ResponseEntity<AgenciaResponse> buscarPorId(@PathVariable Long id) {
        AgenciaResponse response = agenciaService.buscarPorId(id);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza agência")
    public ResponseEntity<AgenciaResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid AgenciaRequest req) {
        AgenciaResponse response = agenciaService.atualizar(id, req);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove agência")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        agenciaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    private void adicionarLinks(AgenciaResponse response) {
        Long id = response.getId();
        response.add(linkTo(methodOn(AgenciaController.class).buscarPorId(id)).withSelfRel());
        response.add(linkTo(methodOn(AgenciaController.class).atualizar(id, null)).withRel("atualizar"));
        response.add(linkTo(methodOn(AgenciaController.class).deletar(id)).withRel("deletar"));
    }
}
