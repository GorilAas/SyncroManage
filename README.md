
# 📱 Syncro Manage

**Syncro Manage** é um aplicativo Android desenvolvido em **Java** no Android Studio. Ele permite o gerenciamento completo de **vendas, serviços e estoque**, com geração de **relatórios em XML e PDF**. 

O app utiliza **Firebase Authentication** para login de usuários e integra com **Turso Tech** (SQLite distribuído) como banco de dados em nuvem, com réplica local e sincronização automática.

---

## ⚙️ Funcionalidades

- 📦 **Controle de Estoque**
  - Cadastro de produtos com custo, valor e quantidade
- 💼 **Gestão de Serviços**
  - Cadastro de serviços com preço e custo
- 💰 **Registro de Vendas**
  - Suporte a vendas de produtos e serviços
  - Controle de quantidade, forma de pagamento e valor total
- 📄 **Geração de Relatórios**
  - Exportação em **XML** e **PDF**
- 🔐 **Login com Firebase Authentication**
  - Os dados do usuário são isolados via **UID**
- 🔄 **Sincronização Automática com Turso**
  - Banco de dados local com sincronização na inicialização ou a cada 2h
  - Geração segura de token via **Cloud Function**

---

## 🧱 Banco de Dados (Turso)

Banco baseado em SQLite, estruturado com índices para performance:

### Tabelas

- `estoque` — produtos (vinculados ao UID do Firebase)
- `servicos` — serviços oferecidos
- `vendas` — registros de venda de produtos ou serviços

### Exemplo de estrutura:

```sql
CREATE TABLE vendas (
  id_venda INTEGER PRIMARY KEY AUTOINCREMENT,
  id_usuario TEXT NOT NULL,
  tipo_item TEXT NOT NULL CHECK (tipo_item IN ('produto', 'servico')),
  id_servico_vendido INTEGER NULL, 
  nome_item_vendido TEXT NOT NULL,
  valor_unitario_vendido REAL NOT NULL, 
  quantidade INTEGER NOT NULL CHECK (quantidade > 0),
  valor_total_venda REAL NOT NULL CHECK (valor_total_venda >= 0),
  data_hora_venda TEXT NOT NULL,
  metodo_pagamento TEXT NOT NULL,
  FOREIGN KEY (id_servico_vendido) REFERENCES servicos(id_servico) ON DELETE SET NULL,
  CONSTRAINT chk_venda_tipo_servico CHECK (
    (tipo_item = 'produto' AND id_servico_vendido IS NULL) OR
    (tipo_item = 'servico' AND id_servico_vendido IS NOT NULL)
  )
);
```

---

## 🔐 Cloud Function para Token de Acesso (Turso)

Uma função HTTP no Google Cloud gera um token JWT da Turso com base no ID Token do Firebase do usuário:

### Pontos principais:
- Verifica o token com o Firebase Admin SDK
- Gera token com permissão `full-access` para Turso
- Token é válido por 2 horas
- Integração feita com `axios`

Você encontra o código da função em: [`cloud/getTursoToken.js`](./cloud/getTursoToken.js)

---

## 🛠️ Tecnologias Usadas

- **Java** (Android)
- **Android Studio**
- **Firebase Authentication**
- **Turso (SQLite distribuído)**
- **Google Cloud Functions**
- **iText PDF**
- **Custom DAOs + DatabaseManager**

---

## 🚀 Como Usar

1. **Clone o repositório**:
   ```bash
   git clone https://github.com/seu-usuario/syncro-manage.git
   ```

2. **Configure o Firebase**:
   - Ative o **Authentication (Email/Password ou Google)**
   - Obtenha o arquivo `google-services.json` e adicione ao seu projeto Android

3. **Implante a Cloud Function**:
   - Crie uma função `getTursoToken` com o código fornecido
   - Configure as variáveis de ambiente:
     - `TURSO_PLATFORM_API_TOKEN`
     - `TURSO_ORG_SLUG`
     - `TURSO_DB_NAME`

4. **Rode o app no Android Studio**

---

## 📄 Licença

Este projeto está licenciado sob a Licença MIT. Veja o arquivo [LICENSE](./LICENSE) para mais detalhes.

---

## 🙌 Contribuições

Contribuições são bem-vindas! Abra uma *issue* ou envie um *pull request* se quiser sugerir melhorias.
