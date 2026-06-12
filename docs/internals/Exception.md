# Exception — Tratamento de erros

## Formato padrão de todos os erros

Qualquer erro da API retorna sempre o mesmo JSON:

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
| `timestamp` | LocalDateTime | Momento exato em que o erro foi processado pelo handler |
| `status` | int | Código HTTP numérico |
| `error` | String | Mensagem descritiva do erro (ou "Erro interno no servidor" para 500) |
| `path` | String | URI da requisição que gerou o erro (de `HttpServletRequest.getRequestURI()`) |

---

## Mapa de erros por situação

### 400 — Requisição inválida

| Situação | Mensagem típica | Origem |
|----------|----------------|--------|
| Campo obrigatório vazio ou ausente | `"campo: não deve ser nulo"` | `MethodArgumentNotValidException` |
| JSON malformado ou enum desconhecido | `"Corpo da requisição inválido ou mal formatado"` | `HttpMessageNotReadableException` |
| `limiteMin >= limiteMax` no sensor | `"limiteMin deve ser menor que limiteMax"` | `IllegalArgumentException` |
| Nome duplicado no mesmo satélite | `"Já existe um sensor com o nome X neste satélite"` | `IllegalArgumentException` |
| Nome duplicado na mesma missão (satélite) | `"Já existe um satélite com o nome X nesta missão"` | `IllegalArgumentException` |
| Login já cadastrado | `"Login já está em uso"` | `IllegalArgumentException` |
| Campo específico do tipo de sensor ausente | `"unidadeEscala é obrigatório para sensores do tipo TERMICO"` | `IllegalArgumentException` |
| Único DONO tentando sair sem transferir | `"Transfira a propriedade da missão antes de sair"` | `DonoUnicoException` |
| Solicitação já respondida | `"Solicitação já foi respondida"` | `IllegalArgumentException` |
| Operador sem agência tentando entrar na missão | `"Operador não possui agência vinculada"` | `IllegalArgumentException` |

### 401 — Não autenticado

| Situação | Mensagem |
|----------|---------|
| Login ou senha incorretos | `"Credenciais inválidas"` |
| Token ausente em rota protegida | JSON `{"erro":"Nao autenticado"}` — retornado pelo `AuthenticationEntryPoint` customizado |
| Token expirado ou com assinatura inválida | JSON `{"erro":"Nao autenticado"}` — `SecurityFilter` não seta autenticação, Spring Security rejeita |
| Senha da missão errada ao solicitar entrada | `"Senha da missão incorreta"` — `SenhaMissaoInvalidaException` |

### 403 — Sem permissão

| Situação | Mensagem típica | Origem |
|----------|----------------|--------|
| Role insuficiente (ex: MEMBRO tentando editar) | `"Role mínima exigida: SUPERVISOR"` | `AcessoNegadoException` |
| Operador não é membro (em GETs protegidos) | `"Você não é membro desta missão"` | `AcessoNegadoException` |
| Missão sem cowork, agência incompatível | `"Sua agência não tem permissão para entrar nesta missão"` | `AcessoNegadoException` |
| DONO tentando se remover via `DELETE /membros` | `"Use o endpoint /sair para sair da missão"` | `AcessoNegadoException` |
| DONO tentando alterar a própria role | `"Não é possível alterar a própria role"` | `AcessoNegadoException` |
| MEMBRO tentando atualizar status de alerta | `"Role mínima exigida: SUPERVISOR"` | `AcessoNegadoException` |

### 404 — Não encontrado

| Situação | Origem |
|---------|--------|
| Missão, satélite, sensor, leitura ou alerta não encontrado pelo id | `EntityNotFoundException` |
| `missaoId` inexistente no request de satélite | `EntityNotFoundException` |
| `sateliteId` inexistente no request de sensor | `EntityNotFoundException` |
| `sensorId` inexistente no request de leitura | `EntityNotFoundException` |
| `agenciaId` inexistente no request de missão | `EntityNotFoundException` |
| Operador não é membro em operações de escrita (`verificarRole`) | `EntityNotFoundException` |
| Solicitação não encontrada ou de outra missão | `EntityNotFoundException` |

> **Por que `verificarRole` lança 404 e não 403?** Semântica: o service usa `findByMissaoIdAndOperadorId` para buscar o vínculo — se não existe, é "não encontrado", não "sem permissão". Um 403 seria semanticamente errado aqui.

### 409 — Conflito

| Situação | Mensagem | Origem |
|----------|---------|--------|
| Operador já é membro e tenta solicitar novamente | `"Operador já é membro desta missão"` | `OperadorJaMembroException` |
| Já existe solicitação `PENDENTE` | `"Já existe uma solicitação pendente para esta missão"` | `OperadorJaMembroException` |
| Violação de constraint de unicidade no banco | `"Registro já existe ou viola restrição de unicidade"` | `DataIntegrityViolationException` |

### 429 — Muitas requisições

| Situação | Mensagem | Origem |
|----------|---------|--------|
| Mais de 5 falhas de login em 60 segundos | `"Muitas tentativas de login. Aguarde 1 minuto antes de tentar novamente."` | `TooManyRequestsException` |

### 500 — Erro interno

Qualquer exceção não mapeada. Retorna `"Erro interno no servidor"` sem expor detalhes — os detalhes aparecem apenas nos logs com nível `ERROR`.

---

## Exceções customizadas do domínio

| Classe | HTTP | Módulo que lança |
|--------|:----:|----------------|
| `EntityNotFoundException` | 404 | Todos os services |
| `AcessoNegadoException` | 403 | missao, satelite, sensor, leitura, alerta |
| `SenhaMissaoInvalidaException` | 401 | missao |
| `OperadorJaMembroException` | 409 | missao |
| `DonoUnicoException` | 400 | missao |
| `TooManyRequestsException` | 429 | auth (LoginRateLimiter) |

Todas estendem `RuntimeException` — não precisam ser declaradas em assinaturas de método.

---

## Fluxo de tratamento

```
Controller / Service / SecurityFilter lança exceção
         ↓
GlobalExceptionHandler (@RestControllerAdvice)
         ↓
Seleciona o handler mais específico (ordem de especificidade):

  EntityNotFoundException             → 404
  AcessoNegadoException               → 403
  SenhaMissaoInvalidaException        → 401
  OperadorJaMembroException           → 409
  DonoUnicoException                  → 400
  TooManyRequestsException            → 429
  MethodArgumentNotValidException     → 400 (Bean Validation — lista todos os campos inválidos)
  HttpMessageNotReadableException     → 400 (JSON ruim / enum desconhecido)
  IllegalArgumentException            → 400
  DataIntegrityViolationException     → 409 (violação de FK ou unique no banco)
  AccessDeniedException               → 403 (do Spring Security)
  AuthenticationException             → 401 (credenciais inválidas no login)
  Exception (fallback final)          → 500

         ↓
Retorna ErroResponse { timestamp, status, error, path }
```

**Sobre `MethodArgumentNotValidException`:** concatena todos os campos inválidos em uma única string, separados por `"; "`:

```json
{
  "error": "nome: não deve ser vazio; limiteMax: não deve ser nulo"
}
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
