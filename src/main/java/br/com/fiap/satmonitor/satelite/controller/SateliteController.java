package br.com.fiap.satmonitor.satelite.controller;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.missao.controller.MissaoController;
import br.com.fiap.satmonitor.satelite.dto.EstatisticasResponse;
import br.com.fiap.satmonitor.satelite.dto.SateliteRequest;
import br.com.fiap.satmonitor.satelite.dto.SateliteResponse;
import br.com.fiap.satmonitor.satelite.service.SateliteService;
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
@RequestMapping("/satelites")
@RequiredArgsConstructor
@Tag(name = "Satélites", description = "Gerenciamento de satélites das missões")
public class SateliteController {

    private final SateliteService sateliteService;

    @PostMapping
    @Operation(summary = "Cria novo satélite — SUPERVISOR ou DONO da missão")
    @ApiResponse(responseCode = "201")
    public ResponseEntity<SateliteResponse> criar(
            @RequestBody @Valid SateliteRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        SateliteResponse response = sateliteService.criar(req, operadorLogado);
        adicionarLinks(response);

        return ResponseEntity
                .created(linkTo(methodOn(SateliteController.class).buscarPorId(response.getId())).toUri())
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Lista todos os satélites")
    public ResponseEntity<Page<SateliteResponse>> listar(
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        Page<SateliteResponse> page = sateliteService.listar(pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca satélite por id")
    public ResponseEntity<SateliteResponse> buscarPorId(@PathVariable Long id) {
        SateliteResponse response = sateliteService.buscarPorId(id);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/missao/{missaoId}")
    @Operation(summary = "Lista satélites de uma missão")
    public ResponseEntity<Page<SateliteResponse>> listarPorMissao(
            @PathVariable Long missaoId,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        Page<SateliteResponse> page = sateliteService.listarPorMissao(missaoId, pageable)
                .map(r -> { adicionarLinks(r); return r; });

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}/estatisticas")
    @Operation(summary = "Retorna estatísticas agregadas do satélite")
    public ResponseEntity<EstatisticasResponse> buscarEstatisticas(@PathVariable Long id) {
        return ResponseEntity.ok(sateliteService.buscarEstatisticas(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza satélite — SUPERVISOR ou DONO")
    public ResponseEntity<SateliteResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid SateliteRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        SateliteResponse response = sateliteService.atualizar(id, req, operadorLogado);
        adicionarLinks(response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui satélite — apenas DONO")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        sateliteService.deletar(id, operadorLogado);
        return ResponseEntity.noContent().build();
    }

    private void adicionarLinks(SateliteResponse response) {
        Long id = response.getId();
        Long missaoId = response.getMissaoId();

        response.add(linkTo(methodOn(SateliteController.class).buscarPorId(id)).withSelfRel());
        response.add(linkTo(methodOn(SateliteController.class).atualizar(id, null, null)).withRel("atualizar"));
        response.add(linkTo(methodOn(SateliteController.class).deletar(id, null)).withRel("deletar"));
        response.add(linkTo(methodOn(SateliteController.class).buscarEstatisticas(id)).withRel("estatisticas"));
        response.add(Link.of("/sensores/satelite/" + id, "sensores"));
        response.add(linkTo(methodOn(MissaoController.class).buscarPorId(missaoId, null)).withRel("missao"));
    }
}
