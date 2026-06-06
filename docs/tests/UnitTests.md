# Testes unitários — JUnit 5 + Mockito

89 testes unitários cobrindo os 4 services principais sem subir Spring context ou banco de dados.

## Como executar

```bash
# Rodar apenas os testes unitários com relatório JaCoCo
./gradlew test

# Relatório HTML gerado em:
# build/reports/tests/test/index.html        → resultado dos testes
# build/reports/jacoco/test/html/index.html  → cobertura por classe
```

---

## Cobertura atual (JaCoCo)

| Service | Instruções cobertas | % |
|---------|:-------------------:|:---:|
| `AlertaService` | 696 / 696 | **100%** |
| `SensorService` | 2104 / 2188 | **96%** |
| `MissaoService` | 3088 / 3392 | **91%** |
| `SateliteService` | 1436 / 1648 | **87%** |

---

## Arquivos de teste

| Arquivo | Testes | O que cobre |
|---------|:------:|-------------|
| `MissaoServiceTest.java` | 36 | Fluxo completo de missões: criar, buscar, atualizar, deletar, solicitarEntrada, responderSolicitacao, sair, listarMembros, removerMembro, promoverMembro |
| `SateliteServiceTest.java` | 22 | CRUD de satélites com validação de role, nome duplicado, missão inexistente |
| `SensorServiceTest.java` | 24 | Criação dos 4 tipos de sensor, validações de campo específico, limiteMin/Max, nome duplicado, atualizar, deletar |
| `AlertaServiceTest.java` | 20 | Listar com/sem filtro, buscarPorId, listarPorSatelite, atualizarStatus com controle de role |

---

## Padrão de construção dos testes

Todos os testes usam `@ExtendWith(MockitoExtension.class)` — sem Spring context, sem H2, sem rede.

```java
@ExtendWith(MockitoExtension.class)
class MissaoServiceTest {

    @Mock MissaoRepository missaoRepository;
    @Mock OperadorMissaoRepository operadorMissaoRepository;
    // ...

    @InjectMocks MissaoService service;

    @Test
    void caminhoFeliz() {
        // Arrange: configurar o comportamento dos mocks
        when(missaoRepository.findById(1L)).thenReturn(Optional.of(missao));
        when(operadorMissaoRepository.findByMissaoIdAndOperadorId(1L, dono.getId()))
                .thenReturn(Optional.of(vinculoDono));

        // Act: chamar o método real do service
        MissaoResponse resp = service.buscarPorId(1L, dono);

        // Assert: verificar o resultado
        assertThat(resp.getRoleDoOperador()).isEqualTo("DONO");
    }
}
```

---

## Cenários cobertos por service

### MissaoServiceTest (36 testes)

| Método | Cenário |
|--------|---------|
| `criar` | caminho feliz → roleDoOperador=DONO |
| `buscarPorId` | membro válido, missão inexistente (404), não-membro (403) |
| `atualizar` | DONO pode, MEMBRO não pode, missão inexistente |
| `deletar` | DONO pode, SUPERVISOR não pode |
| `solicitarEntrada` | caminho feliz, senha errada (401), sem agência (400), agência incompatível sem cowork (403), agência incompatível com cowork (200), já membro (409), solicitação pendente duplicada (409) |
| `listarSolicitacoes` | SUPERVISOR pode, MEMBRO não pode (403), não-membro (404) |
| `responderSolicitacao` | DONO aprova → cria vínculo, SUPERVISOR rejeita, já respondida (400), MEMBRO tenta aprovar (403), solicitação de outra missão (404) |
| `sair` | membro comum, DONO único não pode (400), DONO com outro DONO pode |
| `listarMembros` | membro pode, não-membro (403) |
| `removerMembro` | DONO remove outro, DONO tenta se remover (403), SUPERVISOR tenta remover (403) |
| `promoverMembro` | DONO promove membro, DONO altera própria role (403), membro-alvo não existe (404) |

### SateliteServiceTest (22 testes)

| Método | Cenário |
|--------|---------|
| `criar` | SUPERVISOR cria, missão inexistente (404), sem vínculo (403), MEMBRO (403), nome duplicado (400) |
| `listarPorMissao` | missão existente, missão inexistente (404) |
| `buscarPorId` | encontrado, não encontrado (404) |
| `atualizar` | SUPERVISOR atualiza, nome duplicado (400), MEMBRO (403), satélite inexistente (404), missão do request inexistente (404) |
| `deletar` | DONO deleta, SUPERVISOR (403), satélite inexistente (404) |

### SensorServiceTest (24 testes)

| Método | Cenário |
|--------|---------|
| `criar` | TERMICO, PRESSAO, RADIACAO, MAGNETOMETRO — caminho feliz para cada tipo |
| `criar` | satélite inexistente (404), MEMBRO (403), limiteMin≥limiteMax (400), nome duplicado (400) |
| `criar` | TERMICO sem unidadeEscala (400), PRESSAO sem tipoPressao (400), RADIACAO sem tipoRadiacao (400), MAGNETOMETRO sem eixosMedicao (400) |
| `listarPorSatelite` | satélite existente, satélite inexistente (404) |
| `buscarPorId` | encontrado, não encontrado (404) |
| `atualizar` | SUPERVISOR atualiza, limiteMin inválido (400), nome duplicado (400), MEMBRO (403), sensor inexistente (404) |
| `deletar` | DONO deleta, SUPERVISOR (403), sensor inexistente (404) |

### AlertaServiceTest (20 testes)

| Método | Cenário |
|--------|---------|
| `listar` | sem filtro → `findAll`, com filtro ATIVO → `findByStatusAlerta`, filtro sem resultados |
| `buscarPorId` | encontrado, não encontrado (404) |
| `listarPorSatelite` | satélite existente, satélite inexistente (404) |
| `atualizarStatus` | SUPERVISOR atualiza, DONO atualiza, MEMBRO (403), não-membro (403), alerta inexistente (404) |

---

## Convenções

- Cada método do service tem um `@Nested` próprio — agrupa testes por funcionalidade
- `@DisplayName` em português descrevendo o cenário
- Cenários de erro usam `assertThatThrownBy(...).isInstanceOf(X.class)` da AssertJ
- Cenários de sucesso usam `assertThat(resp.getX()).isEqualTo(Y)` da AssertJ
- Verificações de chamada a repositórios usam `verify(repo).metodo(...)` ou `verify(repo, never()).metodo(...)`
