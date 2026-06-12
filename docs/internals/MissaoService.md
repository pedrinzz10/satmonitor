# MissaoService — Fluxos internos

Referência para os fluxos de negócio implementados em `MissaoService`. Útil para entender decisões de implementação, edge cases e ordem de verificações.

## Índice

1. [criar](#criar)
2. [listar / buscarPorId / buscarPorNome](#listar--buscarpoid--buscarpornome)
3. [atualizar / deletar](#atualizar--deletar)
4. [solicitarEntrada](#solicitarentrada)
5. [responderSolicitacao](#respondersolicítacao)
6. [sair](#sair)
7. [listarMembros / removerMembro / promoverMembro](#listarmembros--removermembro--promovermembro)
8. [verificarRole — helper privado](#verificarrole--helper-privado)
9. [toResponse — montagem do response](#toresponse--montagem-do-response)
10. [Invariantes](#invariantes)

---

## criar

```
1. resolverAgencia(agenciaId)              ← null se agenciaId for null
2. BCrypt.encode(senhaMissao)
3. save(Missao)                            ← sequence SEQ_MISSAO gera o id
4. save(OperadorMissao { role=DONO, dataEntrada=now() })
5. toResponse(missao, DONO)
```

Dois saves em sequência dentro da mesma `@Transactional`. A senha é encodada **antes** do save — nunca é armazenada em texto puro. O operador logado torna-se o `DONO` automaticamente.

> `operadorDono` na entidade `Missao` é uma referência histórica (dono original) — o controle efetivo de permissões usa `OperadorMissao.role`, não esse campo.

---

## listar / buscarPorId / buscarPorNome

### listar

```
findByMembrosOperadorId(operadorLogado.id, pageable)
→ Para cada missão: findByMissaoIdAndOperadorId → role do operador
→ Monta Page<MissaoResponse> com roleDoOperador preenchido
```

**N+1 por design:** uma query para listar missões + 1 query por missão para buscar o role. Aceitável para a escala do projeto.

### buscarPorId

```
1. findById(id)
   └─ não existe → EntityNotFoundException 404

2. findByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ não é membro → AcessoNegadoException 403  ← direto, sem verificarRole

3. toResponse(missao, vinculo.role)
```

**Ordem importa:** verifica se a missão existe antes de verificar membership. Inverter retornaria 403 mesmo para missões inexistentes — vaza a informação de que a missão não existe.

**Por que AcessoNegadoException (403) aqui e não EntityNotFoundException (404)?**  
Em GETs de leitura, a semântica é "você não tem acesso" (não que o vínculo não exista). Contrasta com `verificarRole` que lança 404 — ver seção sobre verificarRole.

### buscarPorNome

```
findByNomeContainingIgnoreCase(nome, pageable)
→ Público — não verifica membership, não exige autenticação
→ Retorna MissaoResponse com roleDoOperador=MEMBRO fixo (placeholder)
```

---

## atualizar / deletar

```
1. findById(id)             → 404 se não existe
2. verificarRole(id, DONO)  → 404 se não é membro, 403 se role insuficiente
3. Atualiza / deleta
   └─ delete faz CASCADE em: OperadorMissao, SolicitacaoEntrada
      (satélites, sensores, leituras e alertas são removidos por cascade nas entidades filhas)
```

`MissaoUpdateRequest` não tem `senhaMissao` — a senha não é atualizável por este endpoint por design.

---

## solicitarEntrada

```
1. findById(id)
   └─ não existe → EntityNotFoundException 404

2. operador.agencia != null?
   └─ null → IllegalArgumentException 400 ("Operador não possui agência vinculada")

3. missao.permitirCowork == false && agencias diferentes?
   └─ sim → AcessoNegadoException 403

4. BCrypt.matches(req.senha, missao.senhaMissao)
   └─ falhou → SenhaMissaoInvalidaException 401

5. existsByMissaoIdAndOperadorId(id, operadorLogado.id)?
   └─ sim → OperadorJaMembroException 409

6. existsByMissaoIdAndOperadorIdAndStatus(id, op.id, PENDENTE)?
   └─ sim → OperadorJaMembroException 409

7. save(SolicitacaoEntrada { status=PENDENTE })
```

**Por que essa ordem?**

| Verificação | Posição | Motivo |
|-------------|:-------:|--------|
| Agência do operador | 2ª | Sem agência, o operador nunca poderá entrar em missão alguma — falha rápida |
| Cowork | 3ª | Antes da senha: impede que agência incompatível descubra a senha tentando repetidamente |
| Senha | 4ª | Só chega aqui quem já passou pelo filtro de agência |
| Já membro | 5ª | Só verifica o banco após confirmar que a intenção é válida |
| Solicitação pendente | 6ª | Query separada de membership |

---

## responderSolicitacao

```
1. verificarRole(missaoId, operadorLogado.id, SUPERVISOR)
   → 404 se não é membro, 403 se MEMBRO

2. findById(solicitacaoId)
   .filter(s → s.missao.id == missaoId)    ← proteção contra IDOR
   └─ não existe ou de outra missão → EntityNotFoundException 404

3. solicitacao.status != PENDENTE?
   └─ sim → IllegalArgumentException 400 ("Solicitação já foi respondida")

4. Se aprovar:
   solicitacao.status = APROVADO
   save(OperadorMissao { role=MEMBRO, dataEntrada=now() })  ← sempre entra como MEMBRO

5. Se rejeitar:
   solicitacao.status = REJEITADO

6. solicitacao.dataResposta = now()
   solicitacao.respondidoPor = operadorLogado
   save(solicitacao)
```

O `.filter(s → s.missao.id == missaoId)` é uma proteção contra **IDOR** (Insecure Direct Object Reference) — impede que um SUPERVISOR da missão 1 responda solicitações da missão 2 sabendo o id da solicitação.

---

## sair

```
1. findByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ não é membro → EntityNotFoundException 404  ← semântica: vínculo não existe

2. Se role == DONO:
   countByMissaoIdAndRole(id, DONO)
   └─ totalDonos <= 1 → DonoUnicoException 400

3. delete(vinculo) → 204
```

**Por que EntityNotFoundException (404) e não AcessoNegadoException (403)?**  
Semântica: "o vínculo que você quer desfazer não existe" é uma situação de "não encontrado", não de "sem permissão". A distinção é importante — 403 implicaria que o operador sabe que é membro mas não tem direito de sair.

---

## listarMembros / removerMembro / promoverMembro

### listarMembros

```
existsByMissaoIdAndOperadorId(missaoId, operadorLogado.id)
└─ false → AcessoNegadoException 403
findByMissaoId(missaoId) → stream().map(toMembroResponse)
```

Usa `existsBy` (COUNT) em vez de `findBy` para verificar acesso — evita carregar o objeto inteiro só para checar existência. Qualquer role tem acesso.

### removerMembro / promoverMembro

```
1. verificarRole(missaoId, operadorLogado.id, DONO)
2. operadorLogado.id == membroId? → AcessoNegadoException 403
3. findByMissaoIdAndOperadorId(missaoId, membroId) → 404 se não existe
4. delete / setRole + save
```

**Verificar role ANTES de verificar existência do membro:** impede que um não-DONO descubra se um determinado membro existe pelo código HTTP da resposta.

---

## verificarRole — helper privado

Usado por: `atualizar`, `deletar`, `listarSolicitacoes`, `responderSolicitacao`, `removerMembro`, `promoverMembro`.

```java
private void verificarRole(Long missaoId, Long operadorId, RoleMissao roleMinimo) {
    OperadorMissao vinculo = operadorMissaoRepository
            .findByMissaoIdAndOperadorId(missaoId, operadorId)
            .orElseThrow(() -> new EntityNotFoundException("Você não é membro desta missão"));

    if (!vinculo.getRole().temPermissao(roleMinimo)) {
        throw new AcessoNegadoException("Role mínima exigida: " + roleMinimo.name());
    }
}
```

**Fluxo:**
- Não é membro → 404 (entidade não encontrada — o vínculo de autorização não existe)
- É membro mas role insuficiente → 403 (sem permissão)

**Contraste com `buscarPorId` e `listarMembros`:** esses métodos lançam `AcessoNegadoException` (403) diretamente (sem `verificarRole`), porque no contexto de leitura a semântica é "você não tem acesso", não "o recurso não existe".

**`temPermissao` por ordinal:**
```
DONO=0, SUPERVISOR=1, MEMBRO=2
verificarRole(SUPERVISOR) → aceita ordinal <= 1 → DONO ✓ SUPERVISOR ✓ MEMBRO ✗
verificarRole(DONO)       → aceita ordinal <= 0 → DONO ✓ SUPERVISOR ✗ MEMBRO ✗
```

---

## toResponse — montagem do response

```java
private MissaoResponse toResponse(Missao missao, RoleMissao roleDoOperador) {
    int totalMembros   = (int) operadorMissaoRepository.countByMissaoId(missao.getId());
    int totalSatelites = (int) sateliteRepository.countByMissaoId(missao.getId());
    // ...
}
```

Duas queries adicionais por response para contar membros e satélites. Não são carregados via lazy loading das coleções — são queries de COUNT específicas para evitar N+1 em cascata.

---

## Invariantes

| Invariante | Onde é garantido |
|-----------|-----------------|
| `senhaMissao` sempre em BCrypt | `criar` (encode) / `solicitarEntrada` (matches) |
| `senhaMissao` nunca aparece em response | `toResponse` — campo omitido |
| Role inicial de qualquer novo membro é MEMBRO | `responderSolicitacao` cria com `RoleMissao.MEMBRO` fixo |
| Pelo menos 1 DONO sempre presente | `sair` verifica `countByMissaoIdAndRole(DONO) <= 1` |
| DONO pode criar múltiplos DONOs (co-owners) | `promoverMembro` sem restrição de quantidade |
| Operador precisa ter agência para solicitar entrada | `solicitarEntrada` verifica antes da senha |
| DONO não pode alterar a própria role | `promoverMembro` verifica `operadorLogado.id == membroId` |
| DONO não pode se remover via `DELETE /membros` | `removerMembro` verifica `operadorLogado.id == membroId` |
| Solicitação só pode ser respondida uma vez | `responderSolicitacao` verifica `status == PENDENTE` |
| Agência incompatível não testa senha | Verificação de cowork é anterior à verificação de senha |
