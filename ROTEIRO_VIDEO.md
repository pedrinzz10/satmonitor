# Roteiro do Vídeo — SatMonitor
> Arquivo temporário — DELETAR após gravar

---

## BLOCO 1 + 2 — Apresentação (slides Gamma) `⏱ 0:00 – 3:30`

---

### Slide 1 — Capa `⏱ 0:00`

**AÇÃO:** Abrir a apresentação no slide de capa.

**FALA:**
> "Olá, vou apresentar o SatMonitor, uma API REST desenvolvida em Java com Spring Boot para monitoramento de satélites espaciais em tempo real."

---

### Slide 2 — Como funciona `⏱ 0:30`

**AÇÃO:** Avançar para o slide "Como funciona".

**FALA:**
> "O sistema permite que agências espaciais criem missões e adicionem satélites com sensores configurados. Cada sensor tem limites mínimo e máximo — quando uma leitura ultrapassa esses limites, a API gera um alerta automaticamente."
>
> "O ponto chave de integração com IoT é que dispositivos embarcados enviam leituras sem precisar de autenticação. A API classifica cada leitura como NORMAL, ALERTA ou CRÍTICO na mesma requisição."
>
> "O controle de acesso é feito por papéis dentro da missão: o DONO tem controle total, o SUPERVISOR gerencia recursos, e o MEMBRO acompanha dados e alertas."

---

### Slide 3 — Stack `⏱ 2:00`

**AÇÃO:** Avançar para o slide de tecnologias.

**FALA:**
> "A stack é Java 21 com Spring Boot 3.4.5, Spring Security com JWT para autenticação stateless, PostgreSQL como banco de dados, Docker para containerização e Swagger para documentação automática da API."

**AÇÃO:** Após o slide, abrir o Swagger em `http://localhost:8080/swagger-ui.html`.

**FALA:**
> "A documentação está disponível via Swagger, onde é possível ver todos os endpoints organizados por recurso — Auth, Agências, Missões, Satélites, Sensores, Leituras e Alertas. A partir daqui vou demonstrar o funcionamento na prática."

---

## BLOCO 3 — Demonstração `⏱ 3:30 – 10:00`

> **IMPORTANTE — ordem obrigatória:**
> A agência precisa existir antes do registro porque o operador é vinculado a ela no cadastro.
> O endpoint `POST /agencias` é público — não precisa de token.

---

### 3.1 — Criar agência (público, sem token) `⏱ 3:30`

**FALA:**
> "Vou começar criando uma agência espacial. Esse endpoint é público — não precisa de token — porque é a entidade base do sistema. Todo operador é vinculado a uma agência no momento do cadastro."

**`POST /agencias`**
> Sem token
```json
{
  "nome": "Agência Espacial Brasileira",
  "siglaPais": "BR",
  "tipoAgencia": "Governamental"
}
```

**Resposta esperada:** `201 Created`

**FALA:**
> "A resposta já vem com links HATEOAS — self, atualizar e deletar. Esse é o padrão HATEOAS, que permite ao cliente navegar pela API sem precisar conhecer os endpoints de antemão."

---

### 3.2 — Registrar operador `⏱ 4:00`

**FALA:**
> "Agora registro um operador vinculado à agência que acabei de criar. O `agenciaId` é obrigatório no cadastro."

**`POST /auth/registrar`**
> Sem token
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123",
  "nome": "Admin",
  "agenciaId": 1
}
```

**Resposta esperada:** `201 Created`

---

### 3.3 — Login e token JWT `⏱ 4:20`

**FALA:**
> "Faço o login. A API retorna um token JWT válido por 8 horas. Vou copiar esse token e configurar no Postman como variável de ambiente para usar automaticamente em todas as próximas chamadas."

**`POST /auth/login`**
> Sem token
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123"
}
```

**Resposta esperada:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

> "Com esse token configurado, todas as rotas protegidas passam a funcionar."

---

### 3.4 — Criar missão `⏱ 4:50`

**FALA:**
> "Agora crio uma missão vinculada à agência. O operador que cria a missão automaticamente se torna o DONO dela, com permissão total sobre todos os recursos da missão."

**`POST /missoes`**
> Header: `Authorization: Bearer {{token}}`
```json
{
  "nome": "Missão Amazônia-1",
  "descricao": "Monitoramento ambiental da bacia amazônica",
  "dataLancamento": "2024-02-22",
  "status": "ATIVA",
  "senhaMissao": "amazonia123",
  "agenciaId": 1,
  "permitirCowork": true
}
```

**Resposta esperada:** `201 Created`

**FALA:**
> "O campo `permitirCowork` como true permite que outros operadores solicitem entrada nessa missão. O campo `senhaMissao` é exigido na solicitação de entrada — é uma camada extra de controle de acesso."

---

### 3.5 — Fluxo de cowork — segundo operador entra na missão `⏱ 5:40`

**FALA:**
> "O sistema tem um fluxo de colaboração entre operadores. Vou demonstrar isso registrando um segundo operador e mostrando como ele solicita entrada em uma missão existente, e como o DONO aprova essa solicitação."

**Passo 1 — Operador 2 busca a missão pelo nome** (público, sem token)

**FALA:**
> "Antes de solicitar entrada, o operador precisa encontrar a missão. Existe um endpoint público de busca por nome — sem token — que funciona como um diretório de missões abertas."

**`GET /missoes/buscar?nome=Amazônia`**
> Sem token

**Resposta esperada:** lista paginada com a missão. O campo `id` retornado é o que vai usar na solicitação.

---

**Passo 2 — Registrar segundo operador** (sem token)

**`POST /auth/registrar`**
```json
{
  "login": "operador2@sat.dev",
  "senha": "senha456",
  "nome": "Operador Dois",
  "agenciaId": 1
}
```

**Passo 3 — Login do segundo operador** — copiar o token retornado

**`POST /auth/login`**
```json
{
  "login": "operador2@sat.dev",
  "senha": "senha456"
}
```

**Passo 4 — Operador 2 solicita entrada na missão**

**FALA:**
> "Para solicitar entrada, o operador precisa informar a senha da missão. Ela funciona como um código de acesso — sem ela não é possível nem pedir para entrar."

**`POST /missoes/1/solicitar`**
> Header: `Authorization: Bearer <token do operador2>`
```json
{
  "senha": "amazonia123"
}
```

**Resposta esperada:** `201 Created` com `"status": "PENDENTE"`

**Passo 5 — Admin lista solicitações pendentes** (voltar ao token do admin)

**`GET /missoes/1/solicitacoes?status=PENDENTE`**
> Header: `Authorization: Bearer <token do admin>`

**FALA:**
> "Do lado do DONO, aparece a solicitação pendente com os dados do operador e da agência dele. Agora vou aprovar."

**Passo 6 — Admin aprova a solicitação**

**`PATCH /missoes/1/solicitacoes/1/aprovar`**
> Header: `Authorization: Bearer <token do admin>`

**Passo 7 — Listar membros confirmando entrada**

**`GET /missoes/1/membros`**
> Header: `Authorization: Bearer <token do admin>`

**FALA:**
> "A lista de membros agora mostra os dois operadores. O Operador Dois entrou com role MEMBRO — o DONO pode promovê-lo a SUPERVISOR se quiser dar mais permissões."

---

### 3.6 — Criar satélite `⏱ 7:00`

**FALA:**
> "Com a missão criada, adiciono um satélite. Informo as coordenadas orbitais — altitude, inclinação e longitude do nodo ascendente — e o tipo de órbita. LEO significa Low Earth Orbit, órbita baixa, que é a órbita real do satélite Amazônia-1."

**`POST /satelites`**
> Header: `Authorization: Bearer {{token}}`
```json
{
  "nome": "AMAZONIA-1A",
  "dataLancamento": "2024-02-22",
  "tipoOrbita": "LEO",
  "statusSatelite": "ATIVO",
  "missaoId": 1,
  "coordenadas": {
    "altitudeKm": 752.0,
    "inclinacao": 98.4,
    "longitudeNodo": 45.0
  }
}
```

**Resposta esperada:** `201 Created`

---

### 3.7 — Criar sensor `⏱ 7:30`

**FALA:**
> "Agora adiciono um sensor ao satélite. O campo `tipo` define o subtipo — TERMICO, PRESSAO, RADIACAO ou MAGNETOMETRO. Aqui crio um sensor térmico com limite mínimo de menos 10 e máximo de 80 graus. A margem de alerta de 10% cria uma zona de alerta: leituras abaixo de -1 grau ou acima de 71 graus geram um alerta, e fora dos limites absolutos é crítico."

**`POST /sensores`**
> Header: `Authorization: Bearer {{token}}`
```json
{
  "nome": "Sensor Térmico Principal",
  "tipo": "TERMICO",
  "unidade": "°C",
  "limiteMin": -10.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": 1,
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada:** `201 Created`

---

### 3.8 — Enviar leitura normal (IoT, sem token) `⏱ 8:00`

**FALA:**
> "Este é o endpoint mais importante do ponto de vista de integração com IoT. Ele é público — sem token — porque representa um dispositivo embarcado no satélite enviando dados. A data e hora são registradas automaticamente pelo servidor. Vou enviar uma leitura normal de 45 graus."

**`POST /leituras`**
> Sem token
```json
{
  "sensorId": 1,
  "valor": 45.0,
  "latitude": -3.1,
  "longitude": -60.0
}
```

**Resposta esperada:** `201 Created` com `"status": "NORMAL"`

**FALA:**
> "A API calculou que 45 graus está dentro da faixa segura e classificou como NORMAL. Nenhum alerta foi gerado."

---

### 3.9 — Enviar leitura crítica + alerta automático `⏱ 8:20`

**FALA:**
> "Agora simulo uma falha — envio uma leitura de 95 graus, bem acima do limite de 80."

**`POST /leituras`**
> Sem token
```json
{
  "sensorId": 1,
  "valor": 95.0,
  "latitude": -3.1,
  "longitude": -60.0
}
```

**Resposta esperada:** `201 Created` com `"status": "CRITICO"`

**FALA:**
> "A leitura foi classificada como CRITICO e um alerta foi gerado automaticamente pelo sistema, tudo na mesma requisição, sem nenhuma intervenção manual."

---

### 3.10 — Consultar alertas `⏱ 8:45`

**FALA:**
> "Consultando os alertas, vejo o que foi gerado automaticamente."

**`GET /alertas`**
> Header: `Authorization: Bearer {{token}}`

**FALA:**
> "Posso também filtrar por satélite específico."

**`GET /alertas/satelite/1`**
> Header: `Authorization: Bearer {{token}}`

**FALA:**
> "Agora vou reconhecer esse alerta — indicando que um operador tomou ciência."

**`PATCH /alertas/1?novoStatus=RECONHECIDO`**
> Header: `Authorization: Bearer {{token}}`

---

### 3.11 — Estatísticas do satélite `⏱ 9:10`

**FALA:**
> "A API também oferece estatísticas agregadas por satélite — média, mínimo, máximo, total de leituras, quantas estão em alerta, quantas críticas e a data da última leitura recebida."

**`GET /satelites/1/estatisticas`**
> Header: `Authorization: Bearer {{token}}`

---

### 3.12 — Demonstrar controle de acesso `⏱ 9:30`

**FALA:**
> "Por fim, demonstro o controle de acesso. Vou tentar deletar o satélite sem enviar o token."

**`DELETE /satelites/1`**
> Sem token

**Resposta esperada:** `403 Forbidden`

**FALA:**
> "A API bloqueou antes de chegar no controller. O Spring Security garante que rotas protegidas só são acessadas por operadores autenticados com o role adequado."

---

## ENCERRAMENTO `⏱ 9:45 – 10:00`

**FALA:**
> "Em resumo, o SatMonitor é uma API completa para monitoramento de satélites espaciais, com autenticação JWT, controle de acesso por role — DONO, SUPERVISOR e OPERADOR — geração automática de alertas a partir de leituras de sensores e suporte a dispositivos IoT sem autenticação. A documentação completa está disponível no Swagger e o repositório no GitHub. Obrigado."

**AÇÃO:** Finalizar com o Swagger aberto na tela.

---

## Referência rápida — valores válidos dos enums

| Campo | Valores aceitos |
|---|---|
| `StatusMissao` | `PLANEJADA`, `ATIVA`, `ENCERRADA` |
| `TipoOrbita` | `LEO`, `MEO`, `GEO`, `HEO` |
| `StatusSatelite` | `ATIVO`, `STANDBY`, `MANUTENCAO`, `DESATIVADO` |
| `TipoSensor` | `TERMICO`, `PRESSAO`, `RADIACAO`, `MAGNETOMETRO` |
| `UnidadeEscala` (TERMICO) | `CELSIUS`, `FAHRENHEIT`, `KELVIN` |
| `StatusAlerta` | `ATIVO`, `RECONHECIDO`, `RESOLVIDO` |
| `StatusLeitura` (response) | `NORMAL`, `ALERTA`, `CRITICO` |

---

> LEMBRETE: deletar este arquivo após gravar o vídeo.
