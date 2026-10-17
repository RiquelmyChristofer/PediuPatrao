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

A última execução registrada passou com 13 testes, sem falhas. Os testes cobrem serviços de pedidos, clientes, carregamento de usuários, recuperação de senha e respostas de erro da API.

## Verificações de demonstração ainda recomendadas

1. Validar os três perfis de usuário e confirmar acessos permitidos/negados nas páginas e rotas.
2. Criar e avançar um pedido pelo fluxo válido; conferir horários e responsáveis.
3. Tentar uma transição inválida, desconto acima de 20% e cancelamento após saída.
4. Conferir os registros de auditoria como ADMIN ou GERENTE.
5. Solicitar recuperação de senha usando uma conta com e-mail cadastrado; abrir o link no mesmo host indicado por `APP_BASE_URL`, antes de 30 minutos, e confirmar que uma nova senha funciona.

Consulte o `README.md` para o roteiro detalhado de validação manual e as instruções de execução. Confirme no enunciado da atividade se é exigido vídeo ou outra evidência de demonstração.
