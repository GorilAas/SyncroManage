<div style="text-align: center;">
  <img src="https://i.ibb.co/Q3KhThmZ/logo-text.png" alt="Syncro Manage Logo" style="width: 60%;">
</div>

# Syncro Manage

**Syncro Manage** é um aplicativo Android desenvolvido em Java para gerenciar **vendas**, **serviços** e **estoque** de forma simples e eficiente. Ideal para pequenos negócios e autônomos, o app permite gerar relatórios em **PDF** e **XML**, com sincronização na nuvem e armazenamento local.

<p align="center">
  <a href="https://youtu.be/x00pdL5N5oU">
    <img src="https://img.youtube.com/vi/x00pdL5N5oU/0.jpg" alt="Syncro Manage Demo" width="560"/>
  </a>
  <br>
  <em>Clique na imagem acima para assistir AD do Syncro Manage</em>
</p>

---

## ✨ Recursos

| Recurso | Descrição |
|---------|-----------|
| 🛠 **Cadastro de Produtos e Serviços** | Registre e gerencie produtos e serviços facilmente. |
| 💰 **Registro de Vendas** | Acompanhe vendas com detalhes do método de pagamento. |
| 📦 **Controle de Estoque** | Gerencie estoque com custo e preço de venda. |
| 📊 **Relatórios** | Exporte relatórios em PDF ou XML. |
| 🔒 **Autenticação Firebase** | Login seguro via e-mail ou Google. |
| ☁️ **Sincronização na Nuvem** | Integração com banco de dados Turso Tech. |
| 📱 **Armazenamento Local** | Suporte offline com sincronização automática. |

---

## 🛠 Tecnologias Utilizadas

![Java](https://img.shields.io/badge/Java-Android%20Studio-orange)
![Firebase](https://img.shields.io/badge/Firebase-Auth-yellow)
![SQLite](https://img.shields.io/badge/SQLite-Turso%20Tech-blue)
![Google Cloud](https://img.shields.io/badge/Google%20Cloud-Functions-green)

- **Java**: Desenvolvimento no Android Studio.
- **Firebase Auth**: Autenticação segura (e-mail/Google).
- **Turso Tech**: Banco de dados SQLite na nuvem com réplica local.
- **Google Cloud Functions**: Emissão de tokens seguros.
- **SQLite**: Banco local com DAOs para Estoque, Venda, Serviço e Relatórios.

---

## 📸 Galeria

### Fluxogramas
<p align="center">
  <img src="https://i.ibb.co/8LW8pS0W/syncro-diagram-improved-gv.png" alt="Fluxograma 1" width="45%"/>
  <img src="https://i.ibb.co/QFBcKvMy/syncro-dependencies-improved-gv.png" alt="Fluxograma 2" width="45%"/>
</p>

---

## 🚀 Como Executar

### Pré-requisitos
- Android Studio
- Conta no Google Cloud com Functions ativado
- Credenciais do Turso Tech

### Passos
1. Clone o repositório:
   ```bash
   git clone https://github.com/GorilAas/SyncroManage.git
   ```
2. Abra o projeto no **Android Studio**.
3. Configure as variáveis de ambiente para **Google Cloud Functions** com suas credenciais do **Turso Tech**.
4. Execute a função `getTursoToken` no Google Cloud Functions.
5. Compile e rode o app em um emulador ou dispositivo Android.

---

## 📜 Licença

Este projeto está sob a [MIT License](LICENSE).

---

## 🤝 Contribuição

Quer contribuir? Siga os passos:
1. Faça um fork do repositório.
2. Crie uma branch (`git checkout -b feature/SuaFuncionalidade`).
3. Commit suas alterações (`git commit -m 'Adiciona SuaFuncionalidade'`).
4. Push para a branch (`git push origin feature/SuaFuncionalidade`).
5. Abra um Pull Request.

---

## 📬 Feedback

Tem sugestões ou encontrou um bug? Abra uma issue no GitHub ou envie um e-mail para [email@example.com](guilhermebernardessz10@gmail.com).

---

<p align="center">
  <a href="https://github.com/GorilAas/SyncroManage/stargazers">
    <img src="https://img.shields.io/github/stars/GorilAas/SyncroManage?style=social" alt="GitHub stars"/>
  </a>
  <a href="https://github.com/GorilAas/SyncroManage/network">
    <img src="https://img.shields.io/github/forks/GorilAas/SyncroManage?style=social" alt="GitHub forks"/>
  </a>
</p>
