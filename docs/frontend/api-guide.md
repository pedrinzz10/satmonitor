# SatMonitor — Guia completo de integração para o frontend

> Documentação de cada endpoint: o que faz, o que não pode, o que o backend retorna e como o frontend deve tratar os dados.
> Tipos TypeScript completos em [`types.ts`](./types.ts).

---

## Índice

1. [Configuração global](#1-configuração-global)
2. [Formato de erro](#2-formato-de-erro)
3. [Paginação](#3-paginação)
4. [Datas](#4-datas)
5. [HATEOAS](#5-hateoas)
6. [Auth](#6-auth)
7. [Agências](#7-agências)
8. [Missões](#8-missões)
9. [Satélites](#9-satélites)
10. [Sensores](#10-sensores)
11. [Leituras](#11-leituras)
12. [Alertas](#12-alertas)
13. [Tabela de permissões](#13-tabela-de-permissões)
14. [Checklist de implementação](#14-checklist-de-implementação)

---

## 1. Configuração global

### Headers obrigatórios

Toda requisição com body deve enviar:
```
Content-Type: application/json
```

Toda requisição em rota protegida deve enviar:
```
Authorization: Bearer <token>
```

### Rotas públicas (sem token)

| Método | Rota |
|:------:|------|
| POST | `/auth/login` |
| POST | `/auth/registrar` |
| GET | `/agencias/**` |
| POST | `/agencias` |
| GET | `/satelites/**` |
| GET | `/sensores/**` |
| GET | `/leituras/**` |
| POST | `/leituras` |
| GET | `/missoes/buscar?nome=` |

**Tudo fora desta lista exige token.** Se o token estiver ausente ou expirado, a API retorna `401`.

### Interceptores recomendados

O frontend deve configurar dois interceptores globais no cliente HTTP:

**Interceptor de request** — injeta o token automaticamente:
```ts
// antes de enviar qualquer requisição
if (token) headers['Authorization'] = `Bearer ${token}`;
```

**Interceptor de response** — captura erros globais:
```ts
if (status === 401) {
  limparToken();
  redirecionarParaLogin();
}
// para todos os outros erros: extrair response.data.erro e exibir
```

---

## 2. Formato de erro

Todos os erros `4xx` e `5xx` retornam o mesmo formato:

```ts
{
  erro: string,       // mensagem legível
  detalhes?: string,  // informação extra, nem sempre presente
  timestamp: string   // ISO-8601 ex: "2026-06-09T14:00:00"
}
```

| HTTP | Significado | Como tratar no frontend |
|:----:|-------------|------------------------|
| 400 | Campo inválido ou regra de negócio | Exibir `erro` próximo ao campo ou como toast |
| 401 | Token ausente, expirado ou credencial errada | Redirecionar para login |
| 403 | Token válido, sem permissão | Exibir mensagem de acesso negado |
| 404 | Recurso não encontrado | Exibir tela/estado de "não encontrado" |
| 409 | Conflito de dados (já existe, já é membro, etc.) | Exibir `erro` como feedback inline |
| 429 | Muitas tentativas de login | Bloquear botão de login temporariamente |
| 500 | Erro interno | Exibir mensagem genérica de falha |

---

## 3. Paginação

Todos os GETs de lista retornam `Page<T>`:

```ts
{
  content: T[],         // array de itens da página atual
  totalElements: number, // total de registros no banco
  totalPages: number,   // total de páginas
  number: number,       // página atual (começa em 0)
  size: number,         // itens por página
  first: boolean,       // é a primeira página?
  last: boolean,        // é a última página?
  empty: boolean        // content está vazio?
}
```

**Query params de paginação:**
```
?page=0&size=10
?page=1&size=20&sort=nome,asc
?page=0&size=20&sort=dataHoraLeitura,desc
```

**Defaults por endpoint** (quando não enviados):

| Endpoint | `size` | `sort` |
|----------|:------:|--------|
| `GET /agencias` | 10 | `nome,asc` |
| `GET /missoes` | 10 | `nome,asc` |
| `GET /satelites` | 10 | `nome,asc` |
| `GET /sensores` | 10 | `nome,asc` |
| `GET /leituras` | 20 | `dataHoraLeitura,desc` |
| `GET /alertas` | 20 | `dataAlerta,desc` |

**Como implementar paginação na UI:**
- Use `totalPages` e `number` para renderizar controles de navegação.
- Use `totalElements` para exibir "X resultados encontrados".
- Use `first` e `last` para desabilitar botões "anterior" e "próximo".
- Use `empty` para exibir estado vazio sem precisar verificar `content.length`.

---

## 4. Datas

| Tipo Java | Formato JSON | Input HTML recomendado |
|-----------|:------------:|------------------------|
| `LocalDate` | `"YYYY-MM-DD"` | `<input type="date">` |
| `LocalDateTime` | `"YYYY-MM-DDTHH:mm:ss"` | Exibir formatado, nunca editável diretamente |

- Campos `LocalDate` no request: enviar sempre como string `"2026-06-09"`. O valor nativo de `<input type="date">` já está neste formato.
- Campos `LocalDateTime` nos responses: são sempre gerados pelo servidor (ex: `dataHoraLeitura`, `dataAlerta`, `dataEntrada`). **Nunca devem ser enviados pelo frontend.**
- Para exibir, formate com `Intl.DateTimeFormat` ou biblioteca como `date-fns` / `dayjs`.

---

## 5. HATEOAS

Todos os responses de recurso único incluem `_links`. São URLs absolutas prontas para uso.

```ts
const satelite = await api.get<SateliteResponse>('/satelites/1');

// navegar para os sensores deste satélite:
const urlSensores = satelite._links?.['sensores']?.href;

// navegar para as estatísticas:
const urlEstat = satelite._links?.['estatisticas']?.href;
```

**Não construa URLs manualmente** quando `_links` estiver disponível — o backend pode mudar os paths sem quebrar o frontend.

Links marcados com `*` na tabela abaixo só aparecem quando o operador tem permissão para aquela ação:

| Recurso | Links disponíveis |
|---------|-------------------|
| Agência | `self`, `atualizar`, `deletar` |
| Missão | `self`, `membros`, `sair`, `atualizar`*, `deletar`* |
| Membro | `remover`*, `promover`* |
| Satélite | `self`, `atualizar`, `deletar`, `estatisticas`, `sensores`, `missao` |
| Sensor | `self`, `atualizar`, `deletar`, `leituras`, `satelite` |
| Leitura | `self`, `deletar`, `sensor`, `satelite` |
| Alerta | `self`, `atualizar-status` |

---

## 6. Auth

### `POST /auth/registrar` — Criar conta

**O que faz:** cria um novo operador com senha criptografada (BCrypt). Retorna `201` sem body.

**O que NÃO pode:**
- Não é possível registrar com um `login` já existente → `409`.
- `agenciaId` é opcional; se omitido, o operador fica sem agência vinculada. Isso afeta a capacidade dele de solicitar entrada em missões com `permitirCowork=false`.

**Request:**
```ts
{
  login: string,      // obrigatório — recomendado usar e-mail como login
  senha: string,      // obrigatório — enviada em texto puro, BCrypt é aplicado no backend
  nome: string,       // obrigatório
  agenciaId?: number  // opcional — ID de GET /agencias
}
```

**Como tratar no frontend:**
- Populate `agenciaId` com um `<select>` carregado de `GET /agencias?size=100&sort=nome,asc`.
- Nunca exiba ou armazene a senha após o submit.
- Em caso de `409`, mostre feedback inline no campo `login`: "Este login já está em uso."
- Sucesso `201` → redirecionar para tela de login.

---

### `POST /auth/login` — Entrar

**O que faz:** valida credenciais e retorna um JWT com validade de 8 horas.

**O que NÃO pode:**
- A mensagem de erro é sempre `"Credenciais inválidas"` tanto para login inexistente quanto para senha errada — propositalmente genérica por segurança.
- Muitas tentativas seguidas disparam `429 Too Many Requests`.

**Request:**
```ts
{
  login: string,
  senha: string
}
```

**Response `200`:**
```ts
{ token: string }
```

**Como tratar no frontend:**
- Armazene o token em memória (`useState` / store) ou `sessionStorage`. Evite `localStorage` — mais vulnerável a XSS.
- O token não traz informações do usuário no body da resposta. Se precisar do nome/agência do operador logado, faça uma chamada separada após o login (não há endpoint dedicado — leia os dados do JWT ou peça ao backend um endpoint `/me`).
- Em `429`: exiba "Muitas tentativas. Aguarde antes de tentar novamente." e desabilite o botão por um tempo.

---

## 7. Agências

### `POST /agencias` — Criar

**O que faz:** cadastra uma nova agência espacial. Rota pública (não exige token).

**O que NÃO pode:**
- `siglaPais` deve ter **exatamente 2 caracteres**. "BRA" ou "B" retornam `400`.
- Nomes duplicados não são validados no nível de API — podem existir duas agências com o mesmo nome.

**Request:**
```ts
{
  nome: string,         // obrigatório, max 255 chars
  siglaPais: string,    // obrigatório, exatamente 2 chars — ex: "BR", "US"
  tipoAgencia?: string  // opcional, texto livre — ex: "GOVERNAMENTAL", "PRIVADA"
}
```

**Response `201`:**
```ts
{
  id: number,
  nome: string,
  siglaPais: string,    // sempre em UPPERCASE (convertido pelo backend)
  tipoAgencia: string | null,
  dataCadastro: string, // "YYYY-MM-DD" — preenchido pelo servidor, não enviar
  _links: { self, atualizar, deletar }
}
```

**Como tratar no frontend:**
- No input `siglaPais`, use `maxLength={2}` e converta para uppercase no `onChange` para feedback visual imediato.
- `dataCadastro` nunca aparece no form — é gerado pelo backend.

---

### `GET /agencias` — Listar (paginado)

**O que faz:** lista todas as agências. Público, sem token.

**Parâmetros:** `?page=0&size=10&sort=nome,asc`

**Como tratar no frontend:**
- Use para popular `<select>` nos formulários de Missão e Registro. Nesse caso, busque com `size=100` para evitar paginação nos selects.

---

### `GET /agencias/{id}` — Buscar por ID

**O que faz:** retorna uma agência específica. Público.

**Erros:** `404` se não existir.

---

### `PUT /agencias/{id}` — Atualizar

**O que faz:** substitui todos os campos da agência. Exige token.

**O que NÃO pode:** não é possível atualizar parcialmente (não existe PATCH em agências) — enviar sempre todos os campos.

**Request:** mesma estrutura do POST.

---

### `DELETE /agencias/{id}` — Excluir

**O que faz:** remove a agência. Exige token.

**Efeito colateral importante:** missões vinculadas a esta agência passam a ter `agenciaId = null` e `nomeAgencia = null` nas responses — a FK é nullable. O frontend deve tratar `nomeAgencia` como possivelmente nulo em qualquer MissaoResponse.

---

## 8. Missões

### `POST /missoes` — Criar

**O que faz:** cria uma nova missão e automaticamente define o operador autenticado como **DONO** com `role = DONO`.

**O que NÃO pode:**
- `senhaMissao` **nunca é retornada** em nenhuma response. Uma vez criada, não pode ser alterada via PUT — só deletando e recriando.
- Não é possível criar missão sem estar autenticado.

**Request:**
```ts
{
  nome: string,              // obrigatório, max 255 chars
  descricao?: string,        // opcional, textarea
  dataLancamento: string,    // obrigatório, "YYYY-MM-DD"
  status: StatusMissao,      // obrigatório: "PLANEJADA" | "ATIVA" | "ENCERRADA"
  senhaMissao: string,       // obrigatório, mín. 6 chars — type="password"
  agenciaId?: number,        // opcional
  objetivo?: string,         // opcional
  dataFimPrevista?: string,  // opcional, "YYYY-MM-DD"
  permitirCowork?: boolean   // opcional, default false
}
```

**Response `201`:**
```ts
{
  id: number,
  nome: string,
  descricao: string | null,
  dataLancamento: string,
  status: StatusMissao,
  roleDoOperador: "DONO",    // sempre DONO para quem criou
  totalMembros: 1,           // começa com 1 (o próprio criador)
  totalSatelites: 0,
  agenciaId: number | null,
  nomeAgencia: string | null,
  objetivo: string | null,
  dataFimPrevista: string | null,
  permitirCowork: boolean,
  _links: { self, membros, sair, atualizar, deletar }
}
```

**Como tratar no frontend:**
- O campo `senhaMissao` deve usar `type="password"` e aparecer **apenas no formulário de criação** — nunca no de edição.
- Exiba um aviso junto ao campo: _"A senha não pode ser recuperada ou alterada após a criação. Guarde-a com segurança."_
- Após criar, salve o `id` retornado para navegar para a missão.
- Use `roleDoOperador` para decidir quais ações mostrar na UI (ver [seção de permissões](#13-tabela-de-permissões)).

---

### `GET /missoes` — Listar as minhas missões

**O que faz:** retorna apenas as missões em que o operador autenticado é **membro** (qualquer role). Exige token.

**O que NÃO pode:** não lista missões de outros operadores que você não faz parte.

**Parâmetros:** `?page=0&size=10`

**Como tratar no frontend:**
- Use esta rota para o dashboard/home do operador.
- Cada item retorna `roleDoOperador` — use para exibir a badge de role (DONO, SUPERVISOR, MEMBRO) e para controlar botões de ação.

---

### `GET /missoes/buscar?nome={termo}` — Busca pública por nome

**O que faz:** busca missões por nome (like, case-insensitive). Público, sem token.

**O que NÃO pode:** não retorna detalhes sensíveis — apenas dados básicos para o operador descobrir a missão antes de solicitar entrada.

**Como tratar no frontend:**
- Use como "busca de missões" na tela de entrada: operador digita o nome, vê os resultados, clica em "solicitar entrada".

---

### `GET /missoes/{id}` — Buscar por ID

**O que faz:** retorna os detalhes de uma missão. Exige token **e** ser membro.

**O que NÃO pode:** retorna `403` se o operador não for membro, mesmo com token válido. Não retorna `404` neste caso para não revelar que a missão existe.

---

### `PUT /missoes/{id}` — Atualizar

**O que faz:** atualiza os dados da missão. Exige token e role **DONO**.

**O que NÃO pode:**
- `senhaMissao` **não pode ser enviada** nesta rota — o campo é ignorado pelo backend se enviado, mas a intenção é que o formulário de edição simplesmente não tenha esse campo.
- Apenas o DONO pode editar — SUPERVISOR e MEMBRO recebem `403`.

**Request:**
```ts
{
  nome: string,
  descricao?: string,
  dataLancamento: string,
  status: StatusMissao,
  agenciaId?: number,
  objetivo?: string,
  dataFimPrevista?: string,
  permitirCowork?: boolean
}
```

**Como tratar no frontend:**
- O formulário de edição é idêntico ao de criação, exceto pela ausência de `senhaMissao`.
- Renderize o botão "Editar missão" apenas se `roleDoOperador === "DONO"`.

---

### `DELETE /missoes/{id}` — Excluir

**O que faz:** remove a missão e todos os vínculos de membros em cascata. Exige role **DONO**.

**O que NÃO pode:** SUPERVISOR não pode excluir missão → `403`.

**Como tratar no frontend:**
- Exibir apenas para `roleDoOperador === "DONO"`.
- Exibir diálogo de confirmação antes de executar.

---

### `POST /missoes/{id}/solicitar` — Solicitar entrada

**O que faz:** cria uma `SolicitacaoEntrada` com status `PENDENTE`. O operador precisa informar a senha da missão.

**O que NÃO pode:**
- Se `permitirCowork === false` na missão e o operador é de uma agência diferente → `403`.
- Se o operador não tiver agência cadastrada e `permitirCowork === false` → `400`.
- Se já é membro → `409`.
- Se já tem solicitação pendente → `409`.
- Se a senha estiver errada → `401` (não `403` — é autenticação da missão, não de role).

**Request:**
```ts
{ senha: string } // type="password"
```

**Response `200`:**
```ts
{
  id: number,
  operadorId: number,
  nomeOperador: string,
  agenciaId: number | null,
  nomeAgencia: string | null,
  status: "PENDENTE",
  dataSolicitacao: string  // gerado pelo servidor
}
```

**Como tratar no frontend:**
- Mostre o estado atual da solicitação ao usuário: "Sua solicitação está pendente de aprovação."
- Em `409` "já é membro": redirecionar para a missão.
- Em `409` "já tem solicitação pendente": informar que já está aguardando aprovação.
- Em `403` cowork desabilitado: "Esta missão não aceita operadores de outras agências."

---

### `GET /missoes/{id}/solicitacoes` — Listar solicitações

**O que faz:** lista solicitações de entrada na missão. Exige role **SUPERVISOR ou DONO**.

**O que NÃO pode:** MEMBRO recebe `403`.

**Parâmetros:** `?status=PENDENTE` | `APROVADO` | `REJEITADO` (opcional — sem filtro retorna todas)

**Como tratar no frontend:**
- Mostrar este menu apenas para SUPERVISOR e DONO.
- Exibir badge de contador de pendentes no ícone/aba de solicitações.

---

### `PATCH /missoes/{id}/solicitacoes/{solicitacaoId}/aprovar` — Aprovar

**O que faz:** aprova a solicitação → operador vira **MEMBRO** da missão. Exige SUPERVISOR ou DONO. Sem body.

**O que NÃO pode:**
- Solicitação já respondida (APROVADO ou REJEITADO) → `400`.

**Response:** `204 No Content`.

---

### `PATCH /missoes/{id}/solicitacoes/{solicitacaoId}/rejeitar` — Rejeitar

**O que faz:** rejeita a solicitação. Exige SUPERVISOR ou DONO. Sem body.

**Response:** `204 No Content`.

---

### `POST /missoes/{id}/sair` — Sair da missão

**O que faz:** remove o operador autenticado da missão. Sem body (enviar `{}`).

**O que NÃO pode:**
- Se o operador for o **único DONO** da missão → `400` com erro `"Não é possível sair sendo o único dono da missão"`.

**Response:** `204 No Content`.

**Como tratar no frontend:**
- Em caso de `400` (único dono): exibir UI para promover outro membro antes de sair. Ex: "Você é o único dono desta missão. Promova outro membro antes de sair."

---

### `GET /missoes/{id}/membros` — Listar membros

**O que faz:** lista todos os membros e suas roles. Qualquer membro pode consultar.

**Response:** array de `MembroResponse` (não é paginado):
```ts
[{
  operadorId: number,
  nome: string,
  login: string,
  role: RoleMissao,
  dataEntrada: string, // gerado pelo servidor
  _links: { remover?, promover? }  // presentes apenas para DONO
}]
```

**Como tratar no frontend:**
- Os `_links.remover` e `_links.promover` só aparecem quando o operador logado é DONO. Use-os para renderizar os botões de gerenciamento de membros.

---

### `PATCH /missoes/{id}/membros/{membroId}?novoRole=` — Promover/rebaixar

**O que faz:** altera a role de um membro. Exige DONO. `novoRole` é **query param**, sem body.

**Valores de `novoRole`:** `SUPERVISOR` | `MEMBRO`

**O que NÃO pode:**
- DONO não pode alterar a própria role nem se promover/rebaixar via este endpoint.
- Não é possível definir `novoRole=DONO` — transferência de dono não é suportada via este endpoint.

**Response:** `204 No Content`.

---

### `DELETE /missoes/{id}/membros/{membroId}` — Remover membro

**O que faz:** expulsa um membro da missão. Exige DONO.

**O que NÃO pode:**
- DONO não pode se remover via este endpoint. Para sair, usar `POST /missoes/{id}/sair`.

**Response:** `204 No Content`.

---

### `GET /missoes/{id}/alertas` — Alertas dos satélites da missão

**O que faz:** lista alertas de todos os satélites da missão. Exige ser membro.

**Parâmetros:** `?status=ATIVO` | `RECONHECIDO` | `RESOLVIDO` (opcional)

**Como tratar no frontend:**
- Use `?status=ATIVO` para o painel de alertas ativos da missão.
- Exibir contagem de alertas ativos na badge da missão.

---

### `PATCH /missoes/{id}/alertas/{alertaId}?novoStatus=` — Atualizar status de alerta via missão

**O que faz:** atalho para atualizar o status de um alerta a partir do contexto da missão. Exige SUPERVISOR ou DONO. Mesmo comportamento de `PATCH /alertas/{id}`.

**Valores de `novoStatus`:** `RECONHECIDO` | `RESOLVIDO`

---

## 9. Satélites

### `POST /satelites` — Criar

**O que faz:** cria um satélite vinculado a uma missão. Exige SUPERVISOR ou DONO na missão.

**O que NÃO pode:**
- MEMBRO não pode criar → `403`.
- Nome duplicado na mesma missão → `400`.
- `coordenadas` é um objeto aninhado obrigatório — se omitido ou incompleto → `400`.

**Request:**
```ts
{
  nome: string,
  dataLancamento: string,       // "YYYY-MM-DD"
  missaoId: number,
  coordenadas: {
    altitudeKm: number,         // obrigatório
    inclinacao: number,         // obrigatório
    longitudeNodo?: number      // opcional
  },
  tipoOrbita?: TipoOrbita,      // "LEO" | "MEO" | "GEO" | "HEO"
  statusSatelite?: StatusSatelite // "ATIVO" | "STANDBY" | "MANUTENCAO" | "DESATIVADO"
}
```

**Response `201`:**
```ts
{
  id: number,
  nome: string,
  dataLancamento: string,
  altitudeKm: number,           // coordenadas "achatadas" no response (não aninhadas)
  inclinacao: number,
  longitudeNodo: number | null,
  tipoOrbita: TipoOrbita | null,
  statusSatelite: StatusSatelite | null,
  missaoId: number,
  nomeMissao: string,
  totalSensores: number,        // começa em 0
  _links: { self, atualizar, deletar, estatisticas, sensores, missao }
}
```

**Atenção:** no **request**, as coordenadas são um **objeto aninhado** (`coordenadas: { altitudeKm, inclinacao }`). No **response**, elas vêm **achatadas** no nível raiz (`altitudeKm`, `inclinacao`). O frontend deve tratar essa diferença ao popular um formulário de edição.

---

### `GET /satelites` — Listar (público)

**Parâmetros:** `?page=0&size=10&sort=nome,asc`

---

### `GET /satelites/{id}` — Buscar por ID (público)

**Erros:** `404` se não existir.

---

### `GET /satelites/missao/{missaoId}` — Satélites de uma missão (público)

**Erros:** `404` se a missão não existir.

---

### `GET /satelites/{id}/estatisticas` — Estatísticas agregadas (público)

**O que faz:** calcula em tempo real a agregação de todas as leituras de todos os sensores do satélite.

**Response:**
```ts
{
  sateliteId: number,
  nomeSatelite: string,
  mediaValor: number | null,      // null se não houver leituras
  minValor: number | null,
  maxValor: number | null,
  totalLeituras: number,          // 0 se sem leituras
  totalAlertas: number,
  totalCriticos: number,
  ultimaLeitura: string | null    // null se sem leituras
}
```

**Como tratar no frontend:**
- Sempre verificar se `mediaValor`, `minValor`, `maxValor` e `ultimaLeitura` são `null` antes de exibir — satélites novos ainda não têm leituras.
- Exibir "Sem leituras registradas" quando `totalLeituras === 0`.

---

### `PUT /satelites/{id}` — Atualizar

**O que faz:** substitui os dados do satélite. Exige SUPERVISOR ou DONO.

**O que NÃO pode:** MEMBRO não pode → `403`. Nome duplicado na mesma missão → `400`.

**Atenção no request:** enviar `coordenadas` como objeto aninhado, igual ao POST — mesmo que o response venha achatado:
```ts
// CORRETO para PUT:
{ ..., coordenadas: { altitudeKm: 600, inclinacao: 55 } }

// ERRADO para PUT (formato de response, não de request):
{ ..., altitudeKm: 600, inclinacao: 55 }
```

---

### `DELETE /satelites/{id}` — Excluir

**O que faz:** remove o satélite e, em cascata, todos os seus sensores e leituras. Exige DONO.

**O que NÃO pode:** SUPERVISOR não pode excluir → `403`.

**Como tratar no frontend:** exibir aviso sobre a exclusão em cascata: _"Ao excluir este satélite, todos os seus sensores e leituras serão permanentemente removidos."_

---

## 10. Sensores

### `POST /sensores` — Criar

**O que faz:** cria um sensor vinculado a um satélite. Exige SUPERVISOR ou DONO na missão.

**O tipo é imutável** — após a criação, não é possível alterar `tipo` via PUT.

**O que NÃO pode:**
- MEMBRO não pode criar → `403`.
- `limiteMin >= limiteMax` → `400`.
- `margemAlerta` fora de 0–100 → `400`.
- Nome duplicado no mesmo satélite → `400`.
- Enviar campo extra de tipo errado (ex: `unidadeEscala` para PRESSAO) → o campo extra incorreto é simplesmente ignorado, mas o campo correto continua obrigatório.

**Request — campos comuns:**
```ts
{
  nome: string,
  unidade: string,      // texto livre: "graus_C", "hPa", "Gy", "nT"
  limiteMin: number,    // deve ser < limiteMax
  limiteMax: number,
  margemAlerta: number, // 0–100, percentual da faixa que vira zona de ALERTA
  sateliteId: number,
  tipo: TipoSensor      // "TERMICO" | "PRESSAO" | "RADIACAO" | "MAGNETOMETRO"
}
```

**Request — campo extra obrigatório por tipo:**

| `tipo` | Campo extra | Valores |
|--------|:-----------:|---------|
| `TERMICO` | `unidadeEscala` | `"CELSIUS"` \| `"FAHRENHEIT"` \| `"KELVIN"` |
| `PRESSAO` | `tipoPressao` | `"ABSOLUTA"` \| `"RELATIVA"` |
| `RADIACAO` | `tipoRadiacao` | `"IONIZANTE"` \| `"NAO_IONIZANTE"` |
| `MAGNETOMETRO` | `eixosMedicao` | `"X"` \| `"Y"` \| `"Z"` \| `"XY"` \| `"XZ"` \| `"YZ"` \| `"XYZ"` |

**Response `201`:**
```ts
{
  id: number,
  nome: string,
  tipo: TipoSensor,
  unidade: string,
  limiteMin: number,
  limiteMax: number,
  margemAlerta: number,
  sateliteId: number,
  nomeSatelite: string,
  detalhe: string | null,  // valor do campo extra: ex "CELSIUS", "XYZ"
  _links: { self, atualizar, deletar, leituras, satelite }
}
```

**Como tratar o formulário no frontend:**

```
[select tipo]  →  onChange → exibir/ocultar campo extra

tipo === "TERMICO"      → exibir [select unidadeEscala]
tipo === "PRESSAO"      → exibir [select tipoPressao]
tipo === "RADIACAO"     → exibir [select tipoRadiacao]
tipo === "MAGNETOMETRO" → exibir [select eixosMedicao]
```

**Inputs imutáveis no modo edição:**
- O select de `tipo` deve ser `disabled` (ou `readonly`) — o backend ignora qualquer alteração.
- O campo extra (ex: `unidadeEscala`) também deve ser somente leitura no modo edição.
- Exibir aviso: _"O tipo do sensor não pode ser alterado após a criação. Para mudar, exclua e recrie o sensor."_
- O valor atual do campo extra vem em `detalhe` no response — use-o para popular o input readonly.

**Visualização da margem de alerta:**

```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
```

Considere exibir um slider ou barra visual mostrando onde ficam as zonas NORMAL, ALERTA e CRITICO para o usuário entender o impacto da margem antes de salvar.

---

### `GET /sensores` — Listar (público)

**Parâmetros:** `?page=0&size=10`

---

### `GET /sensores/{id}` — Buscar por ID (público)

**Erros:** `404`.

---

### `GET /sensores/satelite/{sateliteId}` — Sensores de um satélite (público)

**Erros:** `404` se o satélite não existir.

---

### `PUT /sensores/{id}` — Atualizar

**O que faz:** atualiza os campos editáveis do sensor. Exige SUPERVISOR ou DONO.

**O que NÃO pode:**
- `tipo` e o campo extra (`unidadeEscala`, etc.) são **ignorados** pelo backend — mesmo que enviados, o valor original é mantido.
- MEMBRO não pode → `403`.

**Request:** mesma estrutura do POST (campos de tipo são aceitos mas ignorados).

---

### `DELETE /sensores/{id}` — Excluir

**O que faz:** remove o sensor e todas as suas leituras em cascata. Exige DONO.

**O que NÃO pode:** SUPERVISOR não pode → `403`.

**Como tratar no frontend:** aviso de exclusão em cascata: _"Todas as leituras deste sensor serão excluídas permanentemente."_

---

## 11. Leituras

### `POST /leituras` — Registrar leitura

**O que faz:** registra uma nova leitura e **automaticamente** calcula o `status` (`NORMAL`, `ALERTA` ou `CRITICO`) com base nos limites do sensor. Se `ALERTA` ou `CRITICO`, um alerta é criado simultaneamente.

**Rota pública — sem token.** Projetada para dispositivos IoT (ESP32).

**O que NÃO pode:**
- Nunca enviar `status` ou `dataHoraLeitura` — são definidos exclusivamente pelo servidor.
- `sensorId` inexistente → `404`.

**Request:**
```ts
{
  valor: number,               // obrigatório — valor medido
  sensorId: number,            // obrigatório
  latitude?: number,           // opcional — posição geográfica
  longitude?: number,          // opcional
  qualidade?: QualidadeLeitura // opcional — "BOA" | "DEGRADADA" | "INVALIDA", default "BOA"
}
```

**Response `201`:**
```ts
{
  id: number,
  valor: number,
  dataHoraLeitura: string,   // "YYYY-MM-DDTHH:mm:ss.SSS" — gerado pelo servidor
  status: StatusLeitura,     // "NORMAL" | "ALERTA" | "CRITICO" — calculado automaticamente
  sensorId: number,
  nomeSensor: string,
  sateliteId: number,
  nomeSatelite: string,
  latitude: number | null,
  longitude: number | null,
  qualidade: QualidadeLeitura,
  _links: { self, deletar, sensor, satelite }
}
```

**Como o status é calculado (para exibir visualmente):**
```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)

valor < limiteMin  → CRITICO
valor > limiteMax  → CRITICO
valor < zonaAlertaMin → ALERTA
valor > zonaAlertaMax → ALERTA
caso contrário     → NORMAL
```

**Caso especial `margemAlerta = 0`:** não existe zona ALERTA. O sistema é binário: NORMAL ou CRITICO.

**Como tratar no frontend:**
- Exibir `status` com cores: NORMAL = verde, ALERTA = amarelo/laranja, CRITICO = vermelho.
- `dataHoraLeitura` vem do servidor — use para exibir o horário exato da leitura, não confiar no clock do cliente.
- `qualidade` afeta a confiabilidade da leitura — considere exibir um ícone de aviso quando `DEGRADADA` ou `INVALIDA`.

---

### `GET /leituras` — Listar todas (público)

**Parâmetros:** `?page=0&size=20&sort=dataHoraLeitura,desc`

**Como tratar:** use para um feed geral de leituras. Para leituras de um sensor ou satélite específico, prefira os endpoints filtrados abaixo.

---

### `GET /leituras/{id}` — Buscar por ID (público)

---

### `GET /leituras/sensor/{sensorId}` — Leituras de um sensor (público)

**Parâmetros:** `?status=NORMAL` | `ALERTA` | `CRITICO` (opcional) + paginação

**Atenção:** `?status=` é **case-sensitive** — usar sempre UPPERCASE.

**Erros:** `404` se o sensor não existir.

---

### `GET /leituras/satelite/{sateliteId}` — Leituras de um satélite (público)

**O que faz:** agrega leituras de todos os sensores do satélite em uma lista unificada.

**Parâmetros:** `?status=CRITICO` (opcional) + paginação

**Erros:** `404` se o satélite não existir.

---

### `DELETE /leituras/{id}` — Excluir

**O que faz:** remove a leitura. Exige SUPERVISOR ou DONO na missão do sensor.

**Efeito colateral:** se a leitura tinha um alerta associado, o alerta **não é removido automaticamente** — ele fica órfão no banco. O frontend deve informar isso ou evitar deletar leituras com alertas pendentes.

**Response:** `204 No Content`.

---

## 12. Alertas

> Alertas **nunca são criados pelo frontend** — são gerados automaticamente pela API quando uma leitura recebe status `ALERTA` ou `CRITICO`. O frontend apenas consulta e atualiza o status.

### Ciclo de vida

```
ATIVO  →  RECONHECIDO  →  RESOLVIDO
```

Não há validação de ordem — qualquer transição é aceita (ex: RESOLVIDO → ATIVO é tecnicamente possível).

---

### `GET /alertas` — Listar alertas das minhas missões

**O que faz:** retorna alertas de todos os satélites das missões do operador autenticado.

**Parâmetros:** `?status=ATIVO` (opcional) + paginação

**Como tratar no frontend:**
- Use `?status=ATIVO` para o painel de alertas pendentes.
- Exibir badge de contagem com `totalElements` da resposta paginada.

---

### `GET /alertas/{id}` — Buscar por ID

**O que faz:** retorna um alerta específico. Exige ser membro da missão do alerta.

---

### `GET /alertas/satelite/{sateliteId}` — Alertas de um satélite (público)

**Parâmetros:** paginação padrão.

**Erros:** `404` se o satélite não existir.

---

### `PATCH /alertas/{id}?novoStatus=` — Atualizar status

**O que faz:** transiciona o status do alerta. Exige SUPERVISOR ou DONO na missão. Sem body. `novoStatus` é **query param**.

**Valores:** `ATIVO` | `RECONHECIDO` | `RESOLVIDO`

**O que NÃO pode:** MEMBRO não pode atualizar → `403`.

**Response `200`:** retorna o `AlertaResponse` atualizado completo.

**Como tratar no frontend:**
- Botão "Reconhecer" → `PATCH ?novoStatus=RECONHECIDO`
- Botão "Resolver" → `PATCH ?novoStatus=RESOLVIDO`
- Renderizar esses botões apenas para SUPERVISOR e DONO.
- Após o PATCH, atualizar o item na lista localmente com o response retornado (evita refetch).

**Response:**
```ts
{
  id: number,
  leituraId: number,
  valorLeitura: number,
  sensorId: number,
  nomeSensor: string,
  sateliteId: number,
  nomeSatelite: string,
  missaoId: number,
  nomeMissao: string,
  tipoAlerta: string,      // "ALERTA" ou "CRITICO"
  descricao: string,       // ex: "Sensor 'X': valor 150.0 fora dos limites [-10.0, 90.0]"
  dataAlerta: string,      // "YYYY-MM-DDTHH:mm:ss" — gerado pelo servidor
  statusAlerta: StatusAlerta,
  _links: { self, atualizar-status }
}
```

---

## 13. Tabela de permissões

Use `missaoResponse.roleDoOperador` para controlar o que exibir na UI.

| Ação | DONO | SUPERVISOR | MEMBRO |
|------|:----:|:----------:|:------:|
| Ver missão, membros, satélites, sensores, leituras | ✓ | ✓ | ✓ |
| Ver alertas da missão | ✓ | ✓ | ✓ |
| Ver solicitações de entrada | ✓ | ✓ | — |
| Criar / editar satélite e sensor | ✓ | ✓ | — |
| Excluir leitura | ✓ | ✓ | — |
| Aprovar / rejeitar solicitações | ✓ | ✓ | — |
| Reconhecer / resolver alertas | ✓ | ✓ | — |
| Editar missão | ✓ | — | — |
| Excluir missão / satélite / sensor | ✓ | — | — |
| Remover / promover membros | ✓ | — | — |

---

## 14. Checklist de implementação

- [ ] Interceptor de request: injeta `Authorization: Bearer <token>` automaticamente
- [ ] Interceptor de response: captura `401` → limpa token e redireciona para login
- [ ] Tratamento global de `ErroResponse` → exibe `erro` como toast ou feedback inline
- [ ] Select de agências: `GET /agencias?size=100&sort=nome,asc` nos forms de Registro e Missão
- [ ] Select de missões: `GET /missoes` (autenticado) no form de Satélite
- [ ] Sensor form criação: select de `tipo` interativo → exibe campo extra correspondente
- [ ] Sensor form edição: `tipo` e campo extra como `disabled`/`readonly` com aviso de imutabilidade
- [ ] Missão form criação: campo `senhaMissao` com aviso de que não pode ser recuperada
- [ ] Missão form edição: sem campo `senhaMissao`
- [ ] Coordenadas: request usa objeto aninhado `{ coordenadas: { altitudeKm, inclinacao } }`, response vem achatado
- [ ] Satélite / Sensor edição: popular form de edição requer adaptar do formato de response para o de request
- [ ] Controle de UI baseado em `roleDoOperador`: botões/forms visíveis conforme permissão
- [ ] Sair de missão: tratar `400` de único DONO com UI de promoção de membro
- [ ] Solicitar entrada: tratar `401` senha errada, `403` cowork, `409` já membro / já pendente
- [ ] Status de leitura: exibir com cores (NORMAL=verde, ALERTA=amarelo, CRITICO=vermelho)
- [ ] Qualidade de leitura: exibir ícone de aviso para `DEGRADADA` e `INVALIDA`
- [ ] Alertas: botões Reconhecer/Resolver visíveis apenas para SUPERVISOR e DONO
- [ ] Estatísticas de satélite: tratar `null` em `mediaValor`, `minValor`, `maxValor`, `ultimaLeitura`
- [ ] Paginação: usar `totalElements`, `totalPages`, `first`, `last`, `empty` para controle da UI
- [ ] Datas: `<input type="date">` para `LocalDate`, nunca enviar `LocalDateTime` (gerado pelo servidor)
- [ ] Status filter: sempre UPPERCASE em `?status=`
