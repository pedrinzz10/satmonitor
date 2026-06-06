# Testes de integração — Postman e PowerShell

241 testes cobrindo todos os endpoints da API, executados contra a aplicação rodando localmente.

## Como usar o Postman

1. Abrir o Postman
2. **Import** → selecionar `docs/tests/SatMonitor.postman_collection.json`
3. Executar as pastas **na ordem**, de cima para baixo
4. Tokens e IDs são **salvos automaticamente** entre requests via scripts — nenhuma variável precisa ser preenchida manualmente

> A pasta **4. Agências** usa `token_dono`, que só é gerado na pasta **3. Auth — Login e Tokens**.

A coleção usa **Collection Variables** internas. Não é necessário criar Environment.

---

## Como usar o script PowerShell

Requisito: API rodando em `http://localhost:8080`.

```powershell
.\testControllers\test-api.ps1
```

Saída esperada: cada teste exibe `[PASS]` ou `[FAIL]` com o nome do cenário.

---

## Estrutura dos testes (18 seções)

| Seção | O que testa |
|-------|------------|
| 1. Health Check | API está no ar |
| 2. Auth — Registro | Criar 4 operadores + erros (duplicado, campo vazio) |
| 3. Auth — Login e Tokens | Login dos 4 operadores → salva tokens automaticamente |
| 4. Agências | CRUD de agências + erros de validação |
| 5. Missões — CRUD | Criar com todos os campos; listar, buscar, atualizar + controle de acesso |
| 6. Missões — Solicitar Entrada | Senha errada/correta, agência incompatível sem cowork, já membro, pendente duplicado |
| 7. Missões — Aprovar/Rejeitar | SUPERVISOR aprova, MEMBRO tenta aprovar → 403, solicitação já respondida → 400 |
| 8. Satélites | CRUD com `tipoOrbita`/`statusSatelite`, GETs públicos, estatísticas, nome duplicado |
| 9. Sensores — Criação | Um request para cada um dos 4 tipos |
| 10. Sensores — Validações | limiteMin≥limiteMax, tipo inválido, campo ausente, nome duplicado |
| 11. Leituras — StatusCalculator | 8 valores de fronteira + campos opcionais + erros |
| 12. Leituras — Consultas | Listagem, filtros `?status=`, sensor/satélite inexistente |
| 13. Estatísticas | Agrega leituras dos sensores do satélite |
| 14. Alertas | Listar, filtrar, reconhecer/resolver + MEMBRO → 403 |
| 15. Leituras — Exclusão | sem token → MEMBRO → SUPERVISOR → DONO |
| 16. Sensores — Exclusão | SUPERVISOR bloqueado, DONO deleta em cascade |
| 17. Satélites — Exclusão | SUPERVISOR bloqueado, DONO deleta em cascade |
| 18. Missões — Exclusão | Saída voluntária, DONO deleta missão |

---

## Variáveis salvas automaticamente

| Variável | Salva em |
|----------|---------|
| `agencia_id` | Seção 4 — Criar agência |
| `token_dono`, `token_membro`, `token_supervisor`, `token_forasteiro` | Seção 3 — Login |
| `missao_id` | Seção 5 — Criar missão |
| `sat_id` | Seção 8 — Criar satélite |
| `sensor_termico_id`, `sensor_pressao_id`, `sensor_radiacao_id`, `sensor_mag_id` | Seção 9 |
| `leitura_normal_id`, `leitura_alerta_id`, `leitura_critico_id` | Seção 11 |
| `alerta_id` | Seção 11 — Alerta gerado automaticamente (leitura CRITICO) |

---

## StatusCalculator — referência rápida

Sensor Térmico após atualização da seção 10: `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%`

```
zonaAlertaMin = -5.0
zonaAlertaMax = 85.0

|──CRITICO──|─ALERTA─|────────NORMAL────────|─ALERTA─|──CRITICO──|
-10         -5       85                      90
```

| Valor | Status esperado |
|:-----:|:---------------:|
| 40.0 | NORMAL |
| 87.0 | ALERTA |
| 150.0 | CRITICO |
| -50.0 | CRITICO |
| -8.0 | ALERTA |
| -10.0 | ALERTA (fronteira inclusiva) |
| 85.0 | NORMAL (fronteira exclusiva) |
