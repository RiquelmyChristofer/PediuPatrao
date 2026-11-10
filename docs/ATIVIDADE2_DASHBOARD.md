# Pediu Patrão — Dashboard Administrativo (Atividade Prática M1 – Parte 2)

Branch de implementação (gitflow): `feature/dashboard-admin` → `develop`.
Stack: Java 21, Spring Boot 3.4, Spring Security, MongoDB, Thymeleaf, Chart.js 2.9.

## 1. Análise do problema

O responsável pela pizzaria precisa acompanhar vendas e operação sem abrir coleção por coleção no banco. O
dashboard consome uma **API REST somente leitura** (`/api/dashboard/**`) que agrega os dados já existentes em
`pedidos`, `produtos` e `clientes`. Não foi criada nova coleção: o dashboard é uma *visão* sobre os dados
operacionais, o que evita duplicação e inconsistência.

### Premissas assumidas (o enunciado traz só o título de cada RF)

| Premissa | Decisão |
|---|---|
| Quem acessa? | "Responsável pelo estabelecimento" = perfis **ADMIN** e **GERENTE**. ATENDENTE não acessa. |
| O que é "venda"? | Pedido com status diferente de `CANCELADO`. Faturamento, ticket médio, gráficos e ranking usam só vendas válidas. |
| Data de referência | Data/hora de **entrada** do pedido (`dataHoraEntrada`), no fuso `America/Sao_Paulo` (configurável por `app.dashboard.timezone`). |
| Período padrão | Últimos 30 dias, incluindo hoje. Máximo de 366 dias por consulta. |
| Receita do produto | Subtotal do item (antes do desconto do pedido). |
| Comparativo | Mesmo intervalo imediatamente anterior, com os mesmos filtros. |
| Dados antigos | Status legados (`PENDENTE`, `EM_PREPARO`, `ENTREGUE`…) são normalizados; valores nulos viram zero; pedidos sem `dataHoraEntrada` ficam fora das consultas por período. |

## 2. Requisitos

### 2.1 Funcionais

| ID | Requisito | Como foi atendido |
|---|---|---|
| RF01 | Indicadores gerais de vendas | Cards: faturamento, pedidos válidos, ticket médio, itens vendidos, cancelamentos (qtde e %), total de descontos e variação vs. período anterior. `GET /api/dashboard/resumo` |
| RF02 | Análise de vendas (gráficos) | Vendas por dia/semana/mês (linha de faturamento + barras de pedidos), horários de pico e faturamento por categoria. `GET /vendas`, `/vendas-por-hora`, `/vendas-por-categoria` |
| RF03 | Situação dos pedidos | Gráfico de pedidos por status e tabela dos pedidos mais recentes. `GET /pedidos-por-status`, `/pedidos-recentes` |
| RF04 | Produtos mais vendidos | Ranking (top N, ordenável por quantidade ou receita). `GET /produtos-mais-vendidos` |
| RF05 | Filtros de consulta | Período (com atalhos), status e categoria de produto; agrupamento do gráfico e critério do ranking. Todos os endpoints aceitam os mesmos filtros. `GET /filtros` devolve as opções. |
| RF06 | Integração com a API | A página (`/dashboard`) não recebe dados do servidor: busca tudo via `fetch` nos endpoints acima, com a sessão do usuário logado. |
| RF07 | Atualização e tratamento de dados | Botão "Atualizar" e atualização automática opcional (60 s); estados de carregando/vazio/erro **por seção** (uma falha não derruba o painel); validação de datas no navegador e no servidor; normalização de status e nulos no servidor; mensagem clara quando a sessão expira. |

### 2.2 Não funcionais

| ID | Requisito | Atendimento |
|---|---|---|
| RNF01 | Segurança — acesso restrito | `/dashboard` e `/api/dashboard/**` só para ADMIN/GERENTE, no `SecurityConfig` **e** com `@PreAuthorize` no controller (defesa em profundidade). |
| RNF02 | Segurança — conteúdo | Dados do banco entram na tabela via `textContent` (sem `innerHTML`), evitando XSS. A API não expõe telefone/endereço de clientes (só o nome em "pedidos recentes"). |
| RNF03 | Precisão | Valores monetários em `BigDecimal`, 2 casas, `HALF_UP`. |
| RNF04 | Desempenho | Consulta por intervalo de datas (índice recomendado em `pedidos.dataHoraEntrada`); período limitado a 366 dias; limite do ranking até 50. |
| RNF05 | Usabilidade | Layout responsivo (Bootstrap), rótulos e cores consistentes por status, gráficos com texto alternativo. |
| RNF06 | Manutenibilidade | Camadas controller → service → repository; DTOs imutáveis (`record`); nenhuma alteração nas regras dos módulos existentes. |
| RNF07 | Testabilidade | Serviço com relógio injetável; testes unitários do cálculo e testes de autorização com MockMvc. |
| RNF08 | Consistência | Fuso horário único para filtrar e agrupar (um pedido às 23h30 em São Paulo conta no dia certo). |

## 3. Projeto

```
Navegador (/dashboard)  ──fetch──▶  DashboardController  ──▶  DashboardService  ──▶  PedidoRepository  (pedidos)
   Chart.js + Bootstrap              /api/dashboard/**         agregações/validações   ProdutoRepository (categoria)
                                      @PreAuthorize             período, status,       ClienteRepository (nome)
                                                                categoria
```

**Decisões principais**

1. **Agregação na camada de serviço (Java), não em pipeline do MongoDB.** O volume de uma pizzaria por período
   (≤ 366 dias) é pequeno, a lógica fica testável com Mockito e regras como normalização de status legado e fuso
   horário ficam em um único lugar. Se o volume crescer, trocar por `$group` mantém os mesmos contratos de API.
2. **Vários endpoints pequenos em vez de um "super endpoint".** Cada gráfico/tabela tem seu contrato; a tela
   carrega em paralelo e uma falha isolada não derruba o resto.
3. **Filtros idênticos em todos os endpoints**, para que cards, gráficos e tabelas nunca mostrem recortes diferentes.
4. **Somente leitura.** Nenhum endpoint do dashboard altera dados.
5. **Sem alterar o modelo `Pedido`.** Reaproveita os campos criados na Atividade 1 (itens, valores, status, entrada).

## 4. Documentação da API

Base: `http://localhost:8080`. Autenticação: sessão do formulário de login (cookie `JSESSIONID`).
Perfis permitidos: **ADMIN, GERENTE**. Todos os endpoints são `GET` e retornam `application/json`.

### 4.1 Parâmetros comuns (filtros)

| Parâmetro | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `inicio` | data `AAAA-MM-DD` | não | Início do período (padrão: `fim` − 29 dias). |
| `fim` | data `AAAA-MM-DD` | não | Fim do período, inclusive (padrão: hoje). |
| `status` | texto | não | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO`. |
| `categoria` | texto | não | Tipo do produto (ex.: `Pizza`, `Bebida`). Mantém pedidos que tenham ao menos um item da categoria; ranking e "por categoria" consideram só os itens dela. |

### 4.2 Erros comuns

| HTTP | Quando | Corpo |
|---|---|---|
| 400 | Data inicial > final | `{"erro":"A data inicial não pode ser posterior à data final."}` |
| 400 | Período > 366 dias | `{"erro":"O período máximo de consulta é de 366 dias."}` |
| 400 | Data/número malformado | `{"erro":"Parâmetro 'inicio' inválido. Datas devem seguir o formato AAAA-MM-DD e números devem ser inteiros."}` |
| 400 | Status inexistente | `{"erro":"Status inválido. Valores aceitos: RECEBIDO, EM_PREPARACAO, ..."}` |
| 302 → `/login` | Sem autenticação | Redirecionamento do formulário de login (a tela trata como "sessão expirada"). |
| 403 | Perfil sem permissão (ex.: ATENDENTE) | Página/resposta de acesso negado do Spring Security. |

### 4.3 Endpoints

#### `GET /api/dashboard/resumo` — indicadores gerais (RF01)
Parâmetros: filtros comuns.

```bash
curl -b cookies.txt "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-07"
```
```json
{
  "periodo": {"inicio": "2026-11-01", "fim": "2026-11-07"},
  "faturamentoTotal": 1280.50,
  "totalPedidos": 31, "pedidosValidos": 29, "pedidosCancelados": 2,
  "taxaCancelamentoPercentual": 6.45,
  "ticketMedio": 44.16, "totalDescontos": 38.00, "itensVendidos": 74,
  "comparativo": {
    "faturamentoPeriodoAnterior": 1100.00, "variacaoFaturamentoPercentual": 16.41,
    "pedidosPeriodoAnterior": 27, "variacaoPedidosPercentual": 7.41
  }
}
```
`variacao*` é `null` quando o período anterior não tem vendas. *(valores ilustrativos)*

#### `GET /api/dashboard/vendas` — vendas ao longo do tempo (RF02)
Parâmetros: filtros comuns + `agrupamento` = `dia` (padrão) | `semana` (início na segunda) | `mes`.
Dias/semanas/meses sem venda aparecem com zero.
```json
{"periodo": {"inicio":"2026-11-01","fim":"2026-11-03"}, "agrupamento": "dia",
 "pontos": [{"periodo":"2026-11-01","pedidos":0,"faturamento":0.00},
            {"periodo":"2026-11-02","pedidos":2,"faturamento":75.00}]}
```
Erro específico: `400 {"erro":"Agrupamento inválido. Use dia, semana ou mes."}`

#### `GET /api/dashboard/vendas-por-hora` — horários de pico (RF02)
Filtros comuns. Retorna sempre 24 itens: `[{"hora":0,"pedidos":0,"faturamento":0.00}, …, {"hora":23,…}]`.

#### `GET /api/dashboard/vendas-por-categoria` — faturamento por categoria (RF02)
Filtros comuns. `[{"categoria":"Pizza","quantidade":40,"faturamento":1900.00}, …]` ordenado por faturamento.
Produtos sem categoria ou removidos aparecem como `Sem categoria`.

#### `GET /api/dashboard/pedidos-por-status` — situação dos pedidos (RF03)
Filtros comuns. Inclui `CANCELADO` e todos os status (com 0 quando vazio).
```json
{"periodo": {…}, "total": 31,
 "status": [{"status":"RECEBIDO","quantidade":2}, {"status":"EM_PREPARACAO","quantidade":1}, … ]}
```

#### `GET /api/dashboard/pedidos-recentes` — últimos pedidos (RF03)
Filtros comuns + `limite` (1–50, padrão 10). Ordem: entrada mais recente primeiro.
```json
[{"id":"6730…","cliente":"Maria","status":"PRONTO","valorTotal":88.00,
  "dataHoraEntrada":"2026-11-09T22:10:00Z","responsavelRegistro":"atendente01",
  "itens":"Pizza Calabresa x1, Refrigerante 2L x1"}]
```
Erro: `400 {"erro":"O limite deve estar entre 1 e 50."}`

#### `GET /api/dashboard/produtos-mais-vendidos` — ranking (RF04)
Filtros comuns + `limite` (1–50, padrão 10) + `ordenarPor` = `quantidade` (padrão) | `receita`.
Pedidos cancelados não entram.
```json
[{"posicao":1,"produtoId":"66f…","nome":"Pizza Calabresa","categoria":"Pizza","quantidade":23,"receita":1104.00}]
```
Erro: `400 {"erro":"Critério de ordenação inválido. Use quantidade ou receita."}`

#### `GET /api/dashboard/filtros` — opções dos filtros (RF05)
Sem parâmetros.
```json
{"status":["RECEBIDO","EM_PREPARACAO","PRONTO","SAIU_PARA_ENTREGA","RETIRADO","FINALIZADO","CANCELADO"],
 "categorias":["Bebida","Esfiha","Lanche","Pizza"],"fusoHorario":"America/Sao_Paulo",
 "periodoMaximoEmDias":366,"periodoPadraoEmDias":30}
```

### 4.4 Página
`GET /dashboard` (ADMIN/GERENTE) — tela do painel; link "Dashboard" na barra lateral.

## 5. Como executar

```bash
# MongoDB local em mongodb://localhost:27017 (ou configure MONGODB_URI no .env)
./mvnw spring-boot:run                                   # dados reais
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # + 60 dias de pedidos fictícios para demonstração
```
Login inicial: `admin` / `teste123` (troque a senha). Crie um usuário GERENTE em *Usuários*.
Os dados do perfil `demo` são gravados direto nos repositórios (sem auditoria) com `responsavelRegistro = "demo-seed"`.

Índice recomendado (desempenho): `db.pedidos.createIndex({ dataHoraEntrada: 1 })`.

## 6. Testes

### 6.1 Automatizados — `./mvnw test`

| Classe | O que valida |
|---|---|
| `DashboardServiceTest` | faturamento ignora cancelados; ticket médio e taxa de cancelamento; variação vs. período anterior; período padrão; dias sem venda preenchidos; agrupamento semanal; fuso horário nas horas; normalização de status legados; ranking por quantidade/receita e limite; filtros de categoria e status; rejeição de parâmetros inválidos. |
| `SecurityAuthorizationTest` | sem login → redireciona; ATENDENTE → 403 no dashboard; GERENTE/ADMIN → 200; data inválida → 400; desconto/cancelamento só GERENTE (ATENDENTE e ADMIN → 403); POST sem CSRF → 403; auditoria só leitura para ADMIN/GERENTE; gestão de usuários só ADMIN. |

> **Evidência a anexar:** print do terminal com `BUILD SUCCESS` e a contagem "Tests run: …, Failures: 0".
> *(Os testes foram escritos, mas ainda não executados pelo autor desta documentação — execute e cole o resultado aqui.)*

### 6.2 Manuais (roteiro para gravar as evidências)

Use `docs/testes/testar-api.sh` ou o navegador. Preencha a coluna "Resultado" com print/observação.

| # | Cenário | Esperado | Resultado |
|---|---|---|---|
| 1 | Login como ATENDENTE e abrir `/dashboard` | 403; sem link "Dashboard" na barra | |
| 2 | ATENDENTE chama `GET /api/dashboard/resumo` | 403 | |
| 3 | Login como GERENTE e abrir `/dashboard` | Cards e gráficos carregam | |
| 4 | Filtrar últimos 7 dias | Cards e gráficos mudam; "Atualizado às…" | |
| 5 | Filtrar status `CANCELADO` | Faturamento R$ 0,00; só cancelados | |
| 6 | Filtrar categoria `Pizza` | Ranking e categorias só com pizzas | |
| 7 | Data inicial > final | Mensagem no formulário, sem chamada à API | |
| 8 | `GET /api/dashboard/resumo?inicio=2026-13-40` | 400 com mensagem | |
| 9 | Período sem pedidos | Mensagens "Nenhuma venda…" e cards zerados | |
| 10 | Parar a aplicação e clicar em Atualizar | Mensagem de erro; página não quebra | |
| 11 | Criar um pedido e clicar em Atualizar | Totais aumentam | |
| 12 | Ativar "Atualizar a cada 60 s" | Horário de atualização muda sozinho | |

## 7. Roteiro do vídeo (até 3 min)

| Tempo | Conteúdo |
|---|---|
| 0:00–0:20 | Problema: dono precisa de visão de vendas. Decisão: dashboard sobre dados existentes, API somente leitura, acesso ADMIN/GERENTE. |
| 0:20–0:50 | Mostrar `SecurityConfig`/`DashboardController`: regra no backend. Demonstrar ATENDENTE recebendo 403 na API. |
| 0:50–1:50 | Login como GERENTE: cards, gráfico de vendas (trocar dia/semana/mês), status, horários, categorias, ranking, pedidos recentes. |
| 1:50–2:20 | Filtros: período, status, categoria; erro de data; período vazio. |
| 2:20–2:40 | Chamar um endpoint no navegador/curl mostrando o JSON; citar a agregação na camada de serviço e o fuso horário. |
| 2:40–3:00 | Testes (`./mvnw test`) e estrutura gitflow (`develop` / `feature/dashboard-admin`). |
