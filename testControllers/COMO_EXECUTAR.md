# SatMonitor — Como executar o script de testes

Há dois scripts equivalentes. Use o que for mais conveniente para o seu sistema:

| Script | Pré-requisitos | Sistema |
|---|---|---|
| `test-api.ps1` | PowerShell 7+ (já vem no Windows 11) | Windows |
| `test-api.sh` | Git Bash + `jq` | Windows/macOS/Linux |

---

## Opção A — PowerShell (recomendado no Windows)

### 1. Verifique a versão do PowerShell

```powershell
$PSVersionTable.PSVersion
```

Requer **7.0 ou superior**. Se for menor, baixe em: https://github.com/PowerShell/PowerShell/releases

### 2. Suba a aplicação

```powershell
.\gradlew bootRun
```

Aguarde `Started SatmonitorApplication`. A API ficará disponível em `http://localhost:8080`.

> O banco H2 é em memória (`ddl-auto=create-drop`). **Reinicie a aplicação antes de cada rodada** para garantir banco limpo.

### 3. Execute o script

```powershell
.\testControllers\test-api.ps1
```

Se o PowerShell bloquear por política de execução, rode antes:

```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

---

## Opção B — Bash (Git Bash no Windows, terminal no Linux/macOS)

### Pré-requisitos

| Ferramenta | Como verificar | Instalação |
|---|---|---|
| Git Bash | abrir no menu iniciar | vem com o [Git for Windows](https://git-scm.com/download/win) |
| `jq` | `jq --version` no Git Bash | veja abaixo |

### Instalar o jq no Windows (Git Bash)

```bash
curl -L -o /usr/local/bin/jq https://github.com/jqlang/jq/releases/latest/download/jq-windows-amd64.exe
chmod +x /usr/local/bin/jq
jq --version
```

### Executar

No Windows: clique com o botão direito na pasta do projeto → **"Git Bash Here"**

```bash
chmod +x testControllers/test-api.sh   # apenas na primeira vez
./testControllers/test-api.sh
```

---

## Saída esperada

```
╔══════════════════════════════════════════════════════╗
║        SatMonitor — Bateria de Testes da API         ║
╚══════════════════════════════════════════════════════╝

▶ 1. Health check
  ✓ PASS  GET /actuator/health → 200
  ✓ PASS  Aplicação UP

▶ 2. AUTH — Registro de operadores
  ✓ PASS  POST /auth/registrar dono → 201
  ...

══════════════════════════════════════════════════════
  Total de testes : 120
  Passaram         : 120
  Falharam         : 0
══════════════════════════════════════════════════════
```

Se algum teste falhar, o resumo lista os casos com problema:

```
Testes que falharam — verifique e corrija:
   1. POST /satelites (MEMBRO) → 403
   2. GET /leituras/sensor/{id} inexistente → 404
```

**Exit code:** `0` se todos passaram, `1` se algum falhou (útil para CI).

---

## O que o script cobre (18 seções)

### Seção 1 — Health check
- `GET /actuator/health` → verifica se a aplicação está UP

### Seção 2 — AUTH: Registro
| Cenário | HTTP |
|---|:---:|
| Registro bem-sucedido (dono, membro, supervisor, forasteiro) | 201 |
| Login duplicado | 400 |
| Campo obrigatório em branco | 400 |

### Seção 3 — AUTH: Login
| Cenário | HTTP |
|---|:---:|
| Login com credenciais corretas (todos os 4 usuários) | 200 |
| Token JWT preenchido no response | — |

### Seção 4 — Agências: CRUD
| Cenário | HTTP |
|---|:---:|
| Criar agência (dono) | 201 |
| Criar segunda agência | 201 |
| `siglaPais` com 3 caracteres (deve ser 2) | 400 |
| Criar sem token | 403 |
| Listar todas (público) | 200 |
| Buscar por id (público) | 200 |
| Atualizar (dono) | 200 |
| Buscar id inexistente | 404 |

### Seção 5 — Missões: CRUD
| Cenário | HTTP |
|---|:---:|
| Criar missão com `agenciaId`, `objetivo`, `dataFimPrevista` | 201 |
| Criador recebe role DONO automaticamente | — |
| Listar missões do operador | 200 |
| Buscar por id (membro da missão) | 200 |
| Buscar por id (não membro com token) | 403 |
| Buscar por id (sem token) | 403 |
| Atualizar missão (DONO) | 200 |
| Atualizar missão (não membro) | 404 |

### Seção 6 — Missões: Entrar, Sair e DONO único
| Cenário | HTTP |
|---|:---:|
| Entrar com senha errada | 401 |
| Entrar com senha correta | 200 |
| Entrar sendo já membro | 409 |
| MEMBRO que é membro tenta PUT | 403 |
| MEMBRO que é membro tenta DELETE | 403 |
| DONO único tenta sair | 400 |
| SUPERVISOR sai com sucesso | 204 |

### Seção 7 — Missões: Gerenciamento de membros
| Cenário | HTTP |
|---|:---:|
| Listar membros (não membro com token) | 403 |
| Listar membros (qualquer membro) | 200 |
| DONO tentando se remover via `/membros/{id}` | 403 |
| MEMBRO tenta promover outro membro | 403 |
| DONO promove para SUPERVISOR | 200 |
| DONO rebaixa SUPERVISOR → MEMBRO | 200 |
| DONO tenta alterar a própria role | 403 |
| MEMBRO tenta remover outro membro | 403 |
| DONO remove membro com sucesso | 204 |
| Membro removido tenta sair | 404 |

### Seção 8 — Satélites: CRUD
| Cenário | HTTP |
|---|:---:|
| Criar sem token | 403 |
| Criar sendo MEMBRO da missão | 403 |
| Criar sendo SUPERVISOR (com `tipoOrbita` e `statusSatelite`) | 201 |
| Nome duplicado na mesma missão | 400 |
| `missaoId` inexistente | 404 |
| Listar todos (público) | 200 |
| Buscar por id (público) | 200 |
| Listar por missão com `missaoId` inexistente | 404 |
| Listar por missão | 200 |
| Estatísticas sem leituras (zeros) | 200 |
| Atualizar sendo SUPERVISOR (muda `statusSatelite`) | 200 |
| Atualizar sendo DONO | 200 |
| Deletar sendo SUPERVISOR | 403 |
| Buscar id inexistente | 404 |

### Seção 9 — Sensores: Criação dos 4 tipos
| Cenário | HTTP |
|---|:---:|
| Criar `SensorTermico` com `unidadeEscala=CELSIUS` | 201 |
| Criar `SensorPressao` com `tipoPressao=ABSOLUTA` | 201 |
| Criar `SensorRadiacao` com `tipoRadiacao=IONIZANTE` | 201 |
| Criar `Magnetometro` com `eixosMedicao=XYZ` | 201 |

### Seção 10 — Sensores: Validações e controle de acesso
| Cenário | HTTP |
|---|:---:|
| `limiteMin >= limiteMax` | 400 |
| Tipo de sensor inválido | 400 |
| Campo específico ausente para o tipo | 400 |
| Nome duplicado no mesmo satélite | 400 |
| `sateliteId` inexistente | 404 |
| MEMBRO da missão tenta criar | 403 |
| Listar todos (público) | 200 |
| Buscar por id (público) | 200 |
| Listar por `sateliteId` inexistente | 404 |
| Listar por satélite | 200 |
| Atualizar sendo SUPERVISOR | 200 |
| Atualizar sendo DONO | 200 |

### Seção 11 — Leituras: StatusCalculator
Sensor: `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%` → `zonaAlertaMin=-5`, `zonaAlertaMax=85`

| Valor | Campos extras | Status esperado | Regra |
|---:|---|:---:|---|
| `40.0` | `latitude`, `longitude`, `qualidade=BOA` | NORMAL | dentro de [-5, 85] |
| `87.0` | — | ALERTA | entre zonaAlertaMax(85) e limiteMax(90) |
| `150.0` | — | CRITICO | acima de limiteMax(90) |
| `-50.0` | — | CRITICO | abaixo de limiteMin(-10) |
| `-8.0` | — | ALERTA | entre limiteMin(-10) e zonaAlertaMin(-5) |
| `-10.0` | — | ALERTA | exatamente no limiteMin (fronteira inclusiva) |
| `85.0` | — | NORMAL | exatamente na zonaAlertaMax (fronteira exclusiva) |
| `40.0` | `qualidade=DEGRADADA` | NORMAL | campo opcional |
| `sensorId=99999` | — | 404 | sensor inexistente |
| sem `sensorId` | — | 400 | campo obrigatório ausente |

### Seção 12 — Leituras: Consultas e filtros
| Cenário | HTTP |
|---|:---:|
| Listar todas (público) | 200 |
| Buscar por id (verifica links HATEOAS) | 200 |
| Buscar id inexistente | 404 |
| Listar por sensor | 200 |
| Filtrar por `?status=CRITICO` | 200 |
| Filtrar por `?status=ALERTA` | 200 |
| Filtrar por `?status=NORMAL` | 200 |
| Listar por `sensorId` inexistente | 404 |
| Sensor sem leituras (`totalElements=0`) | 200 |
| Listar por satélite | 200 |
| Filtrar por satélite + status | 200 |
| Listar por `sateliteId` inexistente | 404 |

### Seção 13 — Satélites: Estatísticas com leituras
| Cenário | Verificação |
|---|---|
| Estatísticas após leituras | `totalLeituras >= 8`, `totalCriticos >= 2`, `totalAlertas >= 2`, `mediaValor` e `ultimaLeitura` preenchidos |

### Seção 14 — Alertas: Listagem, filtros e gerenciamento
| Cenário | HTTP |
|---|:---:|
| Listar todos (público) | 200 |
| Filtrar por `?status=ATIVO` | 200 |
| Buscar por id | 200 |
| Listar por satélite | 200 |
| Listar por `sateliteId` inexistente | 404 |
| Reconhecer alerta sem token | 403 |
| Reconhecer alerta (SUPERVISOR) | 200 |
| Resolver alerta (DONO) | 200 |

### Seção 15 — Leituras: Exclusão
| Cenário | HTTP |
|---|:---:|
| Deletar sem token | 403 |
| Deletar (não membro da missão) | 403 |
| Deletar sendo MEMBRO da missão | 403 |
| Deletar sendo SUPERVISOR | 204 |
| Deletar leitura já deletada | 404 |
| Deletar sendo DONO | 204 |

### Seção 16 — Sensores: Exclusão
| Cenário | HTTP |
|---|:---:|
| Deletar sendo SUPERVISOR | 403 |
| Deletar sendo DONO | 204 |
| Buscar sensor deletado | 404 |
| Confirma `totalElements=3` após delete | — |

### Seção 17 — Satélites: Exclusão com cascade
| Cenário | HTTP |
|---|:---:|
| Deletar sendo SUPERVISOR | 403 |
| Deletar sendo DONO | 204 |
| Buscar satélite deletado | 404 |

### Seção 18 — Missões: Exclusão e saída voluntária
| Cenário | HTTP |
|---|:---:|
| SUPERVISOR tenta deletar missão | 403 |
| MEMBRO tenta deletar missão | 403 |
| MEMBRO sai voluntariamente | 204 |
| DONO deleta missão | 204 |
| Buscar missão deletada | 404 |

---

## Mapeamento completo de endpoints testados

| Método | Rota | Coberta |
|---|---|:---:|
| POST | `/auth/registrar` | ✅ |
| POST | `/auth/login` | ✅ |
| POST | `/agencias` | ✅ |
| GET | `/agencias` | ✅ |
| GET | `/agencias/{id}` | ✅ |
| PUT | `/agencias/{id}` | ✅ |
| POST | `/missoes` | ✅ |
| GET | `/missoes` | ✅ |
| GET | `/missoes/{id}` | ✅ |
| PUT | `/missoes/{id}` | ✅ |
| DELETE | `/missoes/{id}` | ✅ |
| POST | `/missoes/{id}/entrar` | ✅ |
| POST | `/missoes/{id}/sair` | ✅ |
| GET | `/missoes/{id}/membros` | ✅ |
| DELETE | `/missoes/{id}/membros/{membroId}` | ✅ |
| PATCH | `/missoes/{id}/membros/{membroId}` | ✅ |
| POST | `/satelites` | ✅ |
| GET | `/satelites` | ✅ |
| GET | `/satelites/{id}` | ✅ |
| GET | `/satelites/missao/{missaoId}` | ✅ |
| GET | `/satelites/{id}/estatisticas` | ✅ |
| PUT | `/satelites/{id}` | ✅ |
| DELETE | `/satelites/{id}` | ✅ |
| POST | `/sensores` | ✅ |
| GET | `/sensores` | ✅ |
| GET | `/sensores/{id}` | ✅ |
| GET | `/sensores/satelite/{sateliteId}` | ✅ |
| PUT | `/sensores/{id}` | ✅ |
| DELETE | `/sensores/{id}` | ✅ |
| POST | `/leituras` | ✅ |
| GET | `/leituras` | ✅ |
| GET | `/leituras/{id}` | ✅ |
| GET | `/leituras/sensor/{sensorId}` | ✅ |
| GET | `/leituras/satelite/{sateliteId}` | ✅ |
| DELETE | `/leituras/{id}` | ✅ |
| GET | `/alertas` | ✅ |
| GET | `/alertas/{id}` | ✅ |
| GET | `/alertas/satelite/{sateliteId}` | ✅ |
| PATCH | `/alertas/{id}` | ✅ |
| GET | `/actuator/health` | ✅ |
