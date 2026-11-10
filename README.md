# Pediu Patrão — Evolução M1 Parte 2

## Alterações incluídas nesta versão

- Perfis `ADMIN`, `GERENTE` e `ATENDENTE`, com autorização aplicada no servidor e proteção CSRF habilitada.
- `ADMIN` administra usuários e produtos e consulta pedidos e auditoria. `GERENTE` opera pedidos, concede descontos de até 20%, cancela pedidos antes da saída com justificativa e consulta clientes/auditoria. `ATENDENTE` cadastra clientes e registra/avança pedidos.
- Senhas armazenadas com BCrypt. O campo de senha não é serializado nas respostas JSON.
- Registros de auditoria em MongoDB para operações de clientes, produtos, usuários e pedidos, incluindo ator, perfil, ação e campos alterados quando aplicável.
- Fluxo de pedido validado: `RECEBIDO` → `EM_PREPARACAO` → `PRONTO` → `SAIU_PARA_ENTREGA` ou `RETIRADO` → `FINALIZADO`. Cancelamento e desconto são recusados após a saída.
- Preços, subtotais e totais calculados no servidor. Produtos inativos não podem ser adicionados a pedidos.
- Recuperação de senha por e-mail com token aleatório, armazenado somente como hash, válido por 30 minutos e de uso único. O envio pode ser configurado por SMTP do Brevo através de variáveis locais.
- Configuração do MongoDB e credenciais SMTP externalizadas; `.env` é ignorado pelo Git.
- Interfaces de CRUD e mensagens amigáveis para erros comuns, sem expor detalhes internos do servidor.

## Configuração local

- Requisitos: JDK 21 e MongoDB Community Server.
- Banco padrão: `mongodb://localhost:27017/pizzaria`.
- Para recuperação de senha, copiar `.env.example` para `.env` e preencher os dados SMTP e remetente verificado. Nunca versionar `.env` ou compartilhar a chave SMTP.
- Inicialização no Windows PowerShell, dentro da pasta `PediuPatrao`:

```powershell
.\mvnw.cmd spring-boot:run
```

- A conta de desenvolvimento criada quando não existe é `admin` / `teste123`. Troque a senha antes de qualquer uso compartilhado.

## Verificações automatizadas

Execute na pasta `PediuPatrao`:

```powershell
.\mvnw.cmd test
```

Os testes cobrem serviços de pedidos e clientes, auditoria, autorização, carregamento de perfis, recuperação de senha e respostas de erro da API.

## Roteiro de demonstração dos três perfis

1. Inicie o MongoDB e a aplicação. Entre como `admin` / `teste123` (conta de desenvolvimento, se ainda não tiver sido alterada) e mostre a administração de usuários e produtos. Crie ou confirme uma conta de perfil `GERENTE` e outra `ATENDENTE`.
2. Em uma sessão separada, entre como `GERENTE`: consulte clientes, pedidos e auditoria; aplique um desconto de até 20% e, se possível, cancele um pedido antes da saída com justificativa. Confirme que uma tentativa de acessar a administração de usuários é negada.
3. Em outra sessão, entre como `ATENDENTE`: cadastre um cliente e registre/avance um pedido pelas etapas permitidas. Confirme que a auditoria não pode ser consultada e que ações exclusivas de gerente/admin são negadas.
4. Volte como `ADMIN` ou `GERENTE` e consulte a auditoria: confira ator, perfil, ação e campos alterados, verificando que telefone/endereço estão mascarados.
5. Se houver tempo, solicite recuperação de senha para uma conta com e-mail cadastrado; abra o link no mesmo host indicado por `APP_BASE_URL`, antes de 30 minutos, e confirme que uma nova senha funciona.

Consulte o `README.md` para o roteiro detalhado de validação manual e as instruções de execução. Confirme no enunciado da atividade se é exigido vídeo ou outra evidência de demonstração.


## Dashboard administrativo (Atividade M1 – Parte 2)

Painel em `/dashboard` (perfis ADMIN e GERENTE) alimentado pela API `/api/dashboard/**`.
Documentação completa (requisitos, endpoints, testes, roteiro de vídeo): [`docs/ATIVIDADE2_DASHBOARD.md`](docs/ATIVIDADE2_DASHBOARD.md).
Para demonstrar com dados fictícios: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`.
