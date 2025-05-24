# Syncro Manage

**Syncro Manage** é um aplicativo Android desenvolvido em Java que permite gerenciar **vendas**, **serviços** e **estoque** de forma simples e eficiente. Além disso, o app permite gerar relatórios em **XML** e **PDF**, ideal para pequenos negócios ou profissionais autônomos.
<p align="center">
  <a href="https://youtu.be/x00pdL5N5oU?si=xivI8rVOwJGX2dk0">
    <img src="https://img.youtube.com/vi/x00pdL5N5oU/0.jpg" alt="Assista à propaganda" width="100%"/>
  </a>
</p>
---

## Recursos principais

- Cadastro de produtos e serviços
- Registro de vendas com método de pagamento
- Controle de estoque com custo e valor de venda
- Geração de relatórios em PDF ou XML
- Autenticação com Firebase (e-mail ou Google)
- Sincronização com banco de dados na nuvem (Turso Tech)
- Sincronização periódica ou ao abrir o app
- Armazenamento local com réplica e sincronização automática

---

## Tecnologias utilizadas

- **Java** (Android Studio)
- **Firebase Auth** (login/registro)
- **Turso Tech** (SQLite na nuvem + réplica local)
- **Google Cloud Function** (emissão de token seguro)
- **SQLite** local com DAOs organizadas (Estoque, Venda, Serviço, Relatório)
<p align="center">
  <img src="https://i.ibb.co/ynT8jDH2/image.png" alt="Imagem de propaganda" width="60%"/>
</p>
---

<p align="center">
  <img src="https://i.ibb.co/Pv7CnfN7/image.png" alt="Fluxograma 1" width="100%"/>
</p>

<p align="center">
  <img src="https://i.ibb.co/gM17RZJk/image.png" alt="Fluxograma 2" width="100%"/>
</p>

---

## Como rodar o projeto

1. Clone o repositório:
   ```bash
   git clone https://github.com/GorilAas/SyncroManage.git
   ```
2. Abra no Android Studio
3. Configure as variáveis de ambiente da Cloud Function com suas credenciais Turso
4. Execute a função `getTursoToken` no Google Cloud Functions

---

## Licença

Este projeto está licenciado sob os termos da [MIT License](LICENSE).
