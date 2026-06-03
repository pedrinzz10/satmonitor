package br.com.fiap.satmonitor.missao.controller;

import br.com.fiap.satmonitor.auth.entity.Operador;
import br.com.fiap.satmonitor.missao.dto.*;
import br.com.fiap.satmonitor.missao.enums.RoleMissao;
import br.com.fiap.satmonitor.missao.service.MissaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/missoes")
@RequiredArgsConstructor
@Tag(name = "Missões", description = "Gerenciamento de missões espaciais")
public class MissaoController {

    private final MissaoService missaoService;

    @PostMapping
    @Operation(summary = "Cria nova missão")
    @ApiResponse(responseCode = "201")
    public ResponseEntity<MissaoResponse> criar(
            @RequestBody @Valid MissaoRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        MissaoResponse response = missaoService.criar(req, operadorLogado);
        adicionarLinks(response, RoleMissao.DONO);

        return ResponseEntity
                .created(linkTo(methodOn(MissaoController.class)
                        .buscarPorId(response.getId(), operadorLogado)).toUri())
                .body(response);
    }

    @GetMapping
    @Operation(summary = "Lista missões do operador logado")
    public ResponseEntity<Page<MissaoResponse>> listar(
            @AuthenticationPrincipal Operador operadorLogado,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {

        Page<MissaoResponse> page = missaoService.listar(operadorLogado, pageable)
                .map(r -> {
                    adicionarLinks(r, RoleMissao.valueOf(r.getRoleDoOperador()));
                    return r;
                });

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca missão por id")
    public ResponseEntity<MissaoResponse> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        MissaoResponse response = missaoService.buscarPorId(id, operadorLogado);
        adicionarLinks(response, RoleMissao.valueOf(response.getRoleDoOperador()));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza missão — apenas DONO")
    public ResponseEntity<MissaoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid MissaoUpdateRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        MissaoResponse response = missaoService.atualizar(id, req, operadorLogado);
        adicionarLinks(response, RoleMissao.DONO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui missão — apenas DONO")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        missaoService.deletar(id, operadorLogado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/entrar")
    @Operation(summary = "Entra na missão via senha")
    public ResponseEntity<MissaoResponse> entrar(
            @PathVariable Long id,
            @RequestBody @Valid EntrarMissaoRequest req,
            @AuthenticationPrincipal Operador operadorLogado) {

        MissaoResponse response = missaoService.entrar(id, req, operadorLogado);
        adicionarLinks(response, RoleMissao.MEMBRO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/sair")
    @Operation(summary = "Sai da missão")
    public ResponseEntity<Void> sair(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        missaoService.sair(id, operadorLogado);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/membros")
    @Operation(summary = "Lista membros da missão")
    public ResponseEntity<List<MembroResponse>> listarMembros(
            @PathVariable Long id,
            @AuthenticationPrincipal Operador operadorLogado) {

        List<MembroResponse> membros = missaoService.listarMembros(id, operadorLogado).stream()
                .peek(m -> {
                    m.add(linkTo(methodOn(MissaoController.class)
                            .removerMembro(id, m.getOperadorId(), null)).withRel("remover"));
                    m.add(linkTo(methodOn(MissaoController.class)
                            .promoverMembro(id, m.getOperadorId(), null, null)).withRel("promover"));
                })
                .toList();

        return ResponseEntity.ok(membros);
    }

    @DeleteMapping("/{id}/membros/{membroId}")
    @Operation(summary = "Remove membro — apenas DONO")
    public ResponseEntity<Void> removerMembro(
            @PathVariable Long id,
            @PathVariable Long membroId,
            @AuthenticationPrincipal Operador operadorLogado) {

        missaoService.removerMembro(id, membroId, operadorLogado);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/membros/{membroId}")
    @Operation(summary = "Promove membro — apenas DONO")
    public ResponseEntity<MembroResponse> promoverMembro(
            @PathVariable Long id,
            @PathVariable Long membroId,
            @RequestParam RoleMissao novoRole,
            @AuthenticationPrincipal Operador operadorLogado) {

        MembroResponse response = missaoService.promoverMembro(id, membroId, novoRole, operadorLogado);
        response.add(linkTo(methodOn(MissaoController.class).listarMembros(id, operadorLogado)).withRel("membros"));
        return ResponseEntity.ok(response);
    }

    private void adicionarLinks(MissaoResponse response, RoleMissao role) {
        Long id = response.getId();

        response.add(linkTo(methodOn(MissaoController.class).buscarPorId(id, null)).withSelfRel());
        response.add(linkTo(methodOn(MissaoController.class).listarMembros(id, null)).withRel("membros"));
        response.add(linkTo(methodOn(MissaoController.class).sair(id, null)).withRel("sair"));

        if (role.temPermissao(RoleMissao.DONO)) {
            response.add(linkTo(methodOn(MissaoController.class).atualizar(id, null, null)).withRel("atualizar"));
            response.add(linkTo(methodOn(MissaoController.class).deletar(id, null)).withRel("deletar"));
        }
    }
}
