# Testes manuais — Postman

## Como usar

1. Abrir o Postman
2. **Import** → selecionar `docs/SatMonitor.postman_collection.json`
3. Executar as pastas **na ordem**, de cima para baixo
4. Tokens e IDs são **salvos automaticamente** entre requests via scripts — nenhuma variável precisa ser preenchida manualmente

A coleção usa **Collection Variables** internas. Não é necessário criar Environment.

---

## Estrutura da coleção

| Pasta | O que testa |
|-------|------------|
| 1. Health Check | API está no ar |
| 2. Auth — Registro | Criar 4 operadores + erros (duplicado, campo vazio) |
| 3. Auth — Login e Tokens | Login dos 4 operadores → salva `token_*` automaticamente |
| 4. Missões — CRUD | Criar, listar, buscar, atualizar + controle de acesso |
| 5. Missões — Entrar e Sair | Senha errada/correta, já membro, regra do DONO único |
| 6. Missões — Membros | Listar, promover, rebaixar, remover + restrições |
| 7. Satélites | CRUD completo, GETs públicos, estatísticas sem leituras |
| 8. Sensores — Criação | Um request para cada um dos 4 tipos |
| 9. Sensores — Validações | limiteMin≥limiteMax, tipo inválido, campo ausente, duplicado |
| 10. Leituras — StatusCalculator | 7 valores de fronteira + erros |
| 11. Leituras — Consultas | Listagem, filtros `?status=`, sensor/satélite inexistente |
| 12. Estatísticas | Agrega leituras dos sensores do satélite |
| 13. Leituras — Exclusão | Controle de acesso: sem token → MEMBRO → SUPERVISOR → DONO |
| 14. Sensores — Exclusão | SUPERVISOR bloqueado, DONO deleta em cascade |
| 15. Satélites — Exclusão | SUPERVISOR bloqueado, DONO deleta em cascade |
| 16. Missões — Exclusão | Saída voluntária, DONO deleta missão |

---

## Variáveis salvas automaticamente

| Variável | Salva em |
|----------|---------|
| `token_dono`, `token_membro`, `token_supervisor`, `token_forasteiro` | Pasta 3 — Login |
| `missao_id` | Pasta 4 — Criar missão |
| `missao_solo_id` | Pasta 5 — Criar missão solo |
| `id_dono`, `id_membro`, `id_supervisor` | Pasta 6 — Listar membros |
| `sat_id` | Pasta 7 — Criar satélite |
| `sensor_termico_id`, `sensor_pressao_id`, `sensor_radiacao_id`, `sensor_mag_id` | Pasta 8 — Criar sensores |
| `leitura_normal_id`, `leitura_alerta_id`, `leitura_critico_id` | Pasta 10 — StatusCalculator |
| `sat_descartavel_id` | Pasta 15 — Criar satélite descartável |

---

## StatusCalculator — referência rápida

Sensor Térmico após o PUT da pasta 9: `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%`

```
zonaAlertaMin = -10 + (100 × 0.05) = -5.0
zonaAlertaMax =  90 - (100 × 0.05) = 85.0

|──CRITICO──|─ALERTA─|────────NORMAL────────|─ALERTA─|──CRITICO──|
-10         -5       85                      90
```

| Valor na pasta 10 | Status esperado |
|:-----------------:|:---------------:|
| 40.0 | NORMAL |
| 87.0 | ALERTA |
| 150.0 | CRITICO |
| -50.0 | CRITICO |
| -8.0 | ALERTA |
| -10.0 | ALERTA (fronteira inclusiva) |
| 85.0 | NORMAL (fronteira exclusiva) |
