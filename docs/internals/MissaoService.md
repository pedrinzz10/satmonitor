# MissaoService — Fluxos internos

Referência para os fluxos de negócio implementados em `MissaoService`. Útil para entender decisões de implementação, edge cases e ordem de verificações.

## Índice

1. [criar](#criar)
2. [listar / buscarPorId](#listar--buscarpoid)
3. [atualizar / deletar](#atualizar--deletar)
4. [solicitarEntrada](#solicitarentrada)
5. [responderSolicitacao](#respondersolicítacao)
6. [sair](#sair)
7. [listarMembros / removerMembro / promoverMembro](#listarmembros--removermembro--promovermembro)
8. [verificarRole](#verificarrole)
9. [Invariantes](#invariantes)

---

## criar

```
1. BCrypt.encode(senhaMissao)
2. save(Missao)                         ← precisa do id gerado pela sequence
3. save(OperadorMissao { role=DONO })   ← usa o id da missão
4. toResponse(missao, DONO)
```

Dois saves em sequência dentro da mesma transação. `senhaMissao` encriptada antes de persistir — nunca em texto puro.

---

## listar / buscarPorId

**listar:**
```
findByMembrosOperadorId(operadorLogado.id, pageable)
→ Para cada missão: findByMissaoIdAndOperadorId → role do operador
→ Monta Page<MissaoResponse>
```

N+1 por design — aceitável para a escala do projeto.

**buscarPorId:**
```
1. findById(id)
   └─ não existe → EntityNotFoundException 404

2. findByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ não é membro → AcessoNegadoException 403  ← direto, sem verificarRole

3. toResponse(missao, vinculo.role)
```

**Ordem importa:** verifica se a missão existe antes de verificar se o operador é membro. Inverter retornaria 403 mesmo para missões inexistentes — vaza a informação de que a missão não existe.

---

## atualizar / deletar

```
1. findById(id)             → 404 se não existe
2. verificarRole(id, DONO)  → 404 se não é membro, 403 se role insuficiente
3. Atualiza / deleta
   └─ delete faz CASCADE em membros (OperadorMissao)
```

`MissaoUpdateRequest` não tem `senhaMissao` — a senha não é atualizável por este endpoint.

---

## solicitarEntrada

```
1. findById(id)
   └─ não existe → EntityNotFoundException 404

2. operador.agencia != null?
   └─ null → IllegalArgumentException 400 ("não está vinculado a nenhuma agência")

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

**Decisão de ordem:** a verificação de agência e de cowork vem **antes** da senha. Isso evita que um operador de outra agência descubra a senha da missão tentando entrar repetidamente.

---

## responderSolicitacao

```
1. verificarRole(missaoId, operadorLogado.id, SUPERVISOR)
   → 404 se não é membro, 403 se MEMBRO

2. findById(solicitacaoId)
   .filter(s → s.missao.id == missaoId)
   └─ não existe ou de outra missão → EntityNotFoundException 404

3. solicitacao.status != PENDENTE?
   └─ sim → IllegalArgumentException 400 ("Solicitação já foi respondida")

4. Se aprovar:
   solicitacao.status = APROVADO
   save(OperadorMissao { role=MEMBRO, dataEntrada=now() })

5. Se rejeitar:
   solicitacao.status = REJEITADO

6. solicitacao.dataResposta = now()
   solicitacao.respondidoPor = operadorLogado
   save(solicitacao)
```

---

## sair

```
1. findByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ não é membro → EntityNotFoundException 404  ← semântica: "vínculo não existe"

2. Se role == DONO:
   countByMissaoIdAndRole(id, DONO)
   └─ totalDonos <= 1 → DonoUnicoException 400

3. delete(vinculo) → 204
```

**Por que `EntityNotFoundException` (404) e não `AcessoNegadoException` (403)?** Semântica: "o vínculo que você quer desfazer não existe" é uma situação de "não encontrado", não de "sem permissão".

---

## listarMembros / removerMembro / promoverMembro

**listarMembros:**
```
existsByMissaoIdAndOperadorId → AcessoNegadoException 403 se não é membro
findByMissaoId → stream().map(toMembroResponse)
```

Usa `existsBy` (COUNT) para verificar acesso — mais eficiente que `findBy`. Qualquer role tem acesso.

**removerMembro / promoverMembro:**
```
1. verificarRole(missaoId, operadorLogado.id, DONO)
2. operadorLogado.id == membroId? → AcessoNegadoException 403
3. findByMissaoIdAndOperadorId(missaoId, membroId) → 404 se não existe
4. delete / setRole + save
```

**Ordem: verificar role antes de verificar existência do membro** — evita que um não-DONO descubra se um determinado membro existe pelo código HTTP.

---

## verificarRole

Helper privado usado por `atualizar`, `deletar`, `listarSolicitacoes`, `responderSolicitacao`, `removerMembro`, `promoverMembro`.

```
1. findByMissaoIdAndOperadorId(missaoId, operadorId)
   └─ não existe → EntityNotFoundException 404 ("Você não é membro desta missão")

2. vinculo.role.temPermissao(roleMinimo)?
   └─ false → AcessoNegadoException 403 ("Role mínima exigida: X")
```

**Contraste com `buscarPorId` e `listarMembros`:** esses métodos lançam `AcessoNegadoException` diretamente (sem `verificarRole`), porque a semântica de "não ter acesso" faz mais sentido do que "o recurso de autorização não existe" em contextos de leitura.

**`temPermissao` por ordinal:** `DONO=0`, `SUPERVISOR=1`, `MEMBRO=2`. `this.ordinal() <= minimo.ordinal()`. `verificarRole(SUPERVISOR)` aceita DONO e SUPERVISOR, rejeita MEMBRO.

---

## Invariantes

| Invariante | Onde é garantido |
|-----------|-----------------|
| `senhaMissao` sempre em BCrypt | `criar` (encode) / `solicitarEntrada` (matches) |
| `senhaMissao` nunca aparece em response | `toResponse` — campo omitido |
| Role inicial de qualquer novo membro é MEMBRO | `responderSolicitacao` cria com `MEMBRO` fixo |
| Pelo menos 1 DONO sempre presente | `sair` verifica `countByMissaoIdAndRole` |
| DONO pode criar múltiplos DONOs | `promoverMembro` sem restrição de direção |
| Operador precisa ter agência para solicitar entrada | `solicitarEntrada` verifica antes da senha |
