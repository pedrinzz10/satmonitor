# Exception — Tratamento de erros

## Formato padrão de todos os erros

Qualquer erro da API, de qualquer módulo, retorna sempre o mesmo JSON:

```json
{
  "timestamp": "2026-06-01T14:32:07.123",
  "status": 404,
  "error": "Sensor não encontrado com id: 99",
  "path": "/sensores/99"
}
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `timestamp` | LocalDateTime | Momento exato em que o erro foi processado |
| `status` | int | Código HTTP numérico |
| `error` | String | Mensagem descritiva |
| `path` | String | URI da requisição que gerou o erro |

---

## Mapa de erros por situação

### 400 — Requisição inválida

| Situação | Mensagem típica |
|----------|----------------|
| Campo obrigatório vazio ou ausente | `"campo: não deve ser nulo"` ou `"campo: não deve estar em branco"` |
| JSON malformado ou tipo de enum desconhecido | `"Corpo da requisição inválido ou mal formatado"` |
| `limiteMin >= limiteMax` no sensor | `"limiteMin deve ser menor que limiteMax"` |
| Nome duplicado no mesmo satélite | `"Já existe um sensor com o nome X neste satélite"` |
| Nome duplicado na mesma missão (satélite) | `"Já existe um satélite com o nome X nesta missão"` |
| Login já cadastrado | `"Login já está em uso"` |
| Campo específico do tipo de sensor ausente | `"Campo unidadeEscala é obrigatório para sensores TERMICO"` |
| Único DONO tentando sair sem transferir | `"Transfira a propriedade antes de sair da missão"` |

### 401 — Não autenticado

| Situação | Mensagem |
|----------|---------|
| Login ou senha incorretos | `"Credenciais inválidas"` |
| Token ausente em rota protegida | (Spring Security retorna 401 automaticamente) |
| Token expirado ou com assinatura inválida | (Spring Security retorna 401 automaticamente) |
| Senha da missão errada ao tentar entrar | `"Senha da missão incorreta"` |

> As respostas de autenticação não distinguem "login não existe" de "senha errada" — isso é intencional para impedir enumeração de usuários.

### 403 — Sem permissão

| Situação | Mensagem típica |
|----------|----------------|
| Operador não é membro da missão (em GETs) | `"Você não tem acesso a esta missão"` |
| Role insuficiente (ex: MEMBRO tentando criar satélite) | `"Role mínima exigida: SUPERVISOR"` |
| SUPERVISOR tentando excluir satélite/sensor (exige DONO) | `"Apenas o DONO pode excluir..."` |
| DONO tentando se remover via DELETE /membros | `"Use o endpoint /sair para sair da missão"` |
| DONO tentando alterar a própria role | `"Não é possível alterar a própria role"` |

### 404 — Não encontrado

| Situação |
|---------|
| Missão, satélite, sensor ou leitura não encontrado pelo id |
| `missaoId` inexistente no request de satélite |
| `sateliteId` inexistente no request de sensor |
| `sensorId` inexistente no request de leitura |
| Operador não é membro em operações de escrita (verificarRole) |

### 409 — Conflito

| Situação | Mensagem |
|----------|---------|
| Operador já é membro e tenta entrar novamente | `"Operador já é membro desta missão"` |

### 500 — Erro interno

Qualquer exceção não mapeada pelos handlers anteriores. Retorna `"Erro interno no servidor"` sem expor detalhes. Os detalhes aparecem apenas nos logs do servidor com stack trace completo.

---

## Exceções customizadas do domínio

| Classe | HTTP | Módulo que lança |
|--------|:----:|----------------|
| `EntityNotFoundException` | 404 | Todos |
| `AcessoNegadoException` | 403 | missao, satelite, sensor, leitura |
| `SenhaMissaoInvalidaException` | 401 | missao |
| `OperadorJaMembroException` | 409 | missao |
| `DonoUnicoException` | 400 | missao |

Todas estendem `RuntimeException` — não precisam ser declaradas em assinaturas de método.

---

## Fluxo de tratamento

```
Controller / Service lança exceção
         ↓
GlobalExceptionHandler (@RestControllerAdvice)
         ↓
Seleciona o handler mais específico:

  EntityNotFoundException      → 404
  AcessoNegadoException        → 403
  SenhaMissaoInvalidaException → 401
  OperadorJaMembroException    → 409
  DonoUnicoException           → 400
  MethodArgumentNotValidException → 400 (campos inválidos)
  HttpMessageNotReadableException → 400 (JSON ruim / enum desconhecido)
  IllegalArgumentException     → 400
  AccessDeniedException        → 403 ("Acesso negado")
  AuthenticationException      → 401 ("Credenciais inválidas")
  Exception (fallback final)   → 500

         ↓
Retorna ErroResponse { timestamp, status, error, path }
```

---

## Como adicionar nova exceção

**1. Criar a classe em `exception/`:**

```java
public class MinhaNovaException extends RuntimeException {
    public MinhaNovaException(String message) {
        super(message);
    }
}
```

**2. Adicionar o handler no `GlobalExceptionHandler` antes do `handleGeneric`:**

```java
@ExceptionHandler(MinhaNovaException.class)
public ResponseEntity<ErroResponse> handleMinhaNova(MinhaNovaException ex, HttpServletRequest req) {
    log.warn(ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(buildErro(422, ex.getMessage(), req));
}
```

**3. Adicionar à tabela de exceções customizadas acima.**

**4. Commitar:**
```
feat: cria MinhaNovaException (422) para <situação>
```
