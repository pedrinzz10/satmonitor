# Testes manuais — Postman

## Como usar

1. Abrir o Postman
2. **Import** → selecionar `docs/SatMonitor.postman_collection.json`
3. Executar as pastas **na ordem**, de cima para baixo
4. Tokens e IDs são **salvos automaticamente** entre requests via scripts — nenhuma variável precisa ser preenchida manualmente

> **Atenção:** A pasta **4. Agências** usa `token_dono`, que só é gerado na pasta **3. Auth — Login e Tokens**.
> O Collection Runner executa na ordem correta se você rodar a coleção completa de uma vez.

A coleção usa **Collection Variables** internas. Não é necessário criar Environment.

---

## Estrutura da coleção

| Pasta | O que testa |
|-------|------------|
| 1. Health Check | API está no ar |
| 2. Auth — Registro | Criar 4 operadores + erros (duplicado, campo vazio) |
| 3. Auth — Login e Tokens | Login dos 4 operadores → salva `token_*` automaticamente |
| 4. Agências | CRUD de agências + erros de validação (requer token da pasta 3) |
| 5. Missões — CRUD | Criar com `agenciaId`, `objetivo`, `dataFimPrevista`; listar, buscar, atualizar + controle de acesso |
| 6. Missões — Entrar e Sair | Senha errada/correta, já membro, regra do DONO único |
| 7. Missões — Membros | Listar, promover, rebaixar, remover + restrições |
| 8. Satélites | CRUD com `tipoOrbita`/`statusSatelite`, GETs públicos, estatísticas |
| 9. Sensores — Criação | Um request para cada um dos 4 tipos |
| 10. Sensores — Validações | limiteMin≥limiteMax, tipo inválido, campo ausente, duplicado |
| 11. Leituras — StatusCalculator | 8 valores de fronteira + campos opcionais (`latitude`, `longitude`, `qualidade`) + erros |
| 12. Leituras — Consultas | Listagem, filtros `?status=`, sensor/satélite inexistente |
| 13. Estatísticas | Agrega leituras dos sensores do satélite |
| 14. Alertas | Listar, filtrar por status, buscar por satélite, reconhecer/resolver + sem token |
| 15. Leituras — Exclusão | Controle de acesso: sem token → MEMBRO → SUPERVISOR → DONO |
| 16. Sensores — Exclusão | SUPERVISOR bloqueado, DONO deleta em cascade |
| 17. Satélites — Exclusão | SUPERVISOR bloqueado, DONO deleta em cascade |
| 18. Missões — Exclusão | Saída voluntária, DONO deleta missão |

---

## Variáveis salvas automaticamente

| Variável | Salva em |
|----------|---------|
| `agencia_id` | Pasta 4 — Criar agência |
| `token_dono`, `token_membro`, `token_supervisor`, `token_forasteiro` | Pasta 3 — Login |
| `missao_id` | Pasta 5 — Criar missão |
| `missao_solo_id` | Pasta 6 — Criar missão solo |
| `id_dono`, `id_membro`, `id_supervisor` | Pasta 7 — Listar membros |
| `sat_id` | Pasta 8 — Criar satélite |
| `sensor_termico_id`, `sensor_pressao_id`, `sensor_radiacao_id`, `sensor_mag_id` | Pasta 9 — Criar sensores |
| `leitura_normal_id`, `leitura_alerta_id`, `leitura_critico_id` | Pasta 11 — StatusCalculator |
| `alerta_id` | Pasta 11 — Alerta gerado automaticamente (leitura CRITICO) |
| `sat_descartavel_id` | Pasta 17 — Criar satélite descartável |

---

## StatusCalculator — referência rápida

Sensor Térmico após o PUT da pasta 10: `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%`

```
zonaAlertaMin = -10 + (100 × 0.05) = -5.0
zonaAlertaMax =  90 - (100 × 0.05) = 85.0

|──CRITICO──|─ALERTA─|────────NORMAL────────|─ALERTA─|──CRITICO──|
-10         -5       85                      90
```

| Valor na pasta 11 | Status esperado |
|:-----------------:|:---------------:|
| 40.0 (+ lat/lng/qualidade) | NORMAL |
| 87.0 | ALERTA |
| 150.0 | CRITICO |
| -50.0 | CRITICO |
| -8.0 | ALERTA |
| -10.0 | ALERTA (fronteira inclusiva) |
| 85.0 | NORMAL (fronteira exclusiva) |
| 40.0 (qualidade=DEGRADADA) | NORMAL |

---

## Teste via script (alternativa ao Postman)

Para rodar a bateria completa no terminal, há dois scripts equivalentes:

**PowerShell (recomendado no Windows):**
```powershell
.\testControllers\test-api.ps1
```

**Bash (Git Bash no Windows, terminal no Linux/macOS):**
```bash
chmod +x testControllers/test-api.sh
./testControllers/test-api.sh
```

As seções seguem a mesma numeração das pastas da coleção (1–18).
Veja `testControllers/COMO_EXECUTAR.md` para pré-requisitos de cada opção.
