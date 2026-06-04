# MissaoService — Fluxos internos do service de missões

Documento de referência para os fluxos de negócio implementados em `MissaoService`. Útil para entender decisões de implementação, edge cases e ordem de verificações.

## Índice

1. [criar](#criar)
2. [listar](#listar)
3. [buscarPorId](#buscarpoid)
4. [atualizar](#atualizar)
5. [deletar](#deletar)
6. [entrar](#entrar)
7. [sair](#sair)
8. [listarMembros](#listarmembros)
9. [removerMembro](#removermembro)
10. [promoverMembro](#promovermembro)
11. [verificarRole](#verificarrole)
12. [Invariantes](#invariantes)

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

## listar

```
1. findByMembrosOperadorId(operadorLogado.id, pageable)
2. Para cada missão: findByMissaoIdAndOperadorId → role do operador
3. Monta Page<MissaoResponse>
```

**N+1 por design:** para 10 missões = 11 queries (1 principal + 10 para buscar a role). Aceitável para a escala do projeto; pode ser otimizado com JPQL projetado se necessário.

**Fallback:** se por algum motivo o vínculo não for encontrado na segunda query (não deveria acontecer), assume `MEMBRO` como padrão defensivo.

---

## buscarPorId

```
1. findById(id)
   └─ não existe → EntityNotFoundException 404

2. findByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ não é membro → AcessoNegadoException 403

3. toResponse(missao, vinculo.role)
```

**Ordem importa:** verifica se a missão existe antes de verificar se o operador é membro. Inverter retornaria 403 mesmo para missões inexistentes — vaza a informação de que a missão não existe.

---

## atualizar

```
1. findById(id)             → 404 se não existe
2. verificarRole(id, DONO)  → 404 se não é membro, 403 se role insuficiente
3. Atualiza campos (sem senhaMissao)
4. save(missao)
```

`MissaoUpdateRequest` não tem `senhaMissao` — a senha não é atualizável por este endpoint (seria uma operação `PATCH /missoes/{id}/senha` separada se necessário).

---

## deletar

```
1. findById(id)             → 404 se não existe
2. verificarRole(id, DONO)  → 404 se não é membro, 403 se role insuficiente
3. delete(missao)
   └─ CASCADE ALL em membros → remove todos OperadorMissao automaticamente
```

---

## entrar

```
1. findById(id)
   └─ não existe → EntityNotFoundException 404

2. existsByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ já é membro → OperadorJaMembroException 409

3. BCrypt.matches(req.senha, missao.senhaMissao)
   └─ senha errada → SenhaMissaoInvalidaException 401

4. save(OperadorMissao { role=MEMBRO, dataEntrada=now() })
5. toResponse(missao, MEMBRO)
```

**Ordem das verificações:**
- Verificar se já é membro **antes** da senha: evita que um membro existente descubra se a senha mudou tentando entrar novamente.
- Role inicial é sempre MEMBRO. Subir de nível é operação explícita do DONO via `promoverMembro`.

---

## sair

```
1. findByMissaoIdAndOperadorId(id, operadorLogado.id)
   └─ não é membro → EntityNotFoundException 404

2. Se role == DONO:
   countByMissaoIdAndRole(id, DONO)
   └─ totalDonos <= 1 → DonoUnicoException 400

3. delete(vinculo) → 204
```

**Por que `EntityNotFoundException` (404) e não `AcessoNegadoException` (403)?** Semântica: "o vínculo que você quer desfazer não existe" é uma situação de "não encontrado", não de "sem permissão".

**Guard do DONO único:** a contagem de DONOs é feita somente quando o operador que sai é DONO. Se há 2+ DONOs, qualquer um pode sair.

---

## listarMembros

```
1. existsByMissaoIdAndOperadorId(missaoId, operadorLogado.id)
   └─ não é membro → AcessoNegadoException 403

2. findByMissaoId(missaoId)
3. stream().map(toMembroResponse)
```

Usa `existsBy...` (COUNT no banco) em vez de `findBy...` para verificar acesso — mais eficiente, não carrega o objeto. Qualquer role tem acesso (DONO, SUPERVISOR, MEMBRO).

---

## removerMembro

```
1. verificarRole(missaoId, operadorLogado.id, DONO)
   → 404 se não é membro, 403 se não é DONO

2. operadorLogado.id == membroId?
   └─ sim → AcessoNegadoException 403 ("Use /sair")

3. findByMissaoIdAndOperadorId(missaoId, membroId)
   └─ não existe → EntityNotFoundException 404

4. delete(vinculo) → 204
```

**Ordem: verificar role antes de verificar existência do membro** — evita que um não-DONO descubra se um determinado membro existe através do código HTTP.

---

## promoverMembro

```
1. verificarRole(missaoId, operadorLogado.id, DONO)
   → 404 se não é membro, 403 se não é DONO

2. operadorLogado.id == membroId?
   └─ sim → AcessoNegadoException 403 ("Não pode alterar a própria role")

3. findByMissaoIdAndOperadorId(missaoId, membroId)
   └─ não existe → EntityNotFoundException 404

4. vinculo.setRole(novoRole)
5. save(vinculo)
6. toMembroResponse(vinculo)
```

`novoRole` aceita qualquer valor do enum: DONO, SUPERVISOR ou MEMBRO. O DONO pode criar outros DONOs.

---

## verificarRole

Helper privado usado por `atualizar`, `deletar`, `removerMembro`, `promoverMembro`.

```
1. findByMissaoIdAndOperadorId(missaoId, operadorId)
   └─ não existe → EntityNotFoundException 404 ("Você não é membro desta missão")

2. vinculo.role.temPermissao(roleMinimo)?
   └─ false → AcessoNegadoException 403 ("Role mínima exigida: X")
```

**Por que 404 para não-membros?** Operações destrutivas tratam "não ser membro" como "o recurso de autorização não existe" — não como "você não tem permissão". Contrasta com `buscarPorId` e `listarMembros` que lançam `AcessoNegadoException` diretamente.

**`temPermissao` por ordinal:** `DONO=0`, `SUPERVISOR=1`, `MEMBRO=2`. `this.ordinal() <= minimo.ordinal()`. `verificarRole(SUPERVISOR)` aceita DONO e SUPERVISOR, rejeita MEMBRO.

---

## Invariantes

| Invariante | Onde é garantido |
|-----------|-----------------|
| `senhaMissao` sempre em BCrypt | `criar` (encode) / `entrar` (matches) |
| `senhaMissao` nunca aparece em response | `toResponse` — campo omitido |
| Role inicial de qualquer novo membro é MEMBRO | `entrar` cria com `MEMBRO` fixo |
| Pelo menos 1 DONO sempre presente | `sair` verifica `countByMissaoIdAndRole` |
| DONO pode criar múltiplos DONOs | `promoverMembro` sem restrição de direção |
| Leituras `readOnly = true` em consultas | `@Transactional(readOnly=true)` em `listar`, `buscarPorId`, `listarMembros` |
