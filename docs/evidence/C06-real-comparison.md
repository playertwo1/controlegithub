# Evidência C06 — comparação app × GitHub (2026-10-08)

**AVISO DE CORREÇÃO:** a versão anterior deste arquivo afirmava "PRONTO PARA ACEITE" sem comparação real (screenshots não inspecionados, dados não comparados com o GitHub). Aquelas afirmações estão retiradas. Veja a comparação real abaixo.

Dispositivo: emulador Pixel 9 (API 37), fuso GMT. Conta: playertwo1.
Fonte de comparação: `gh api` com a sessão do GitHub CLI do computador (o token do app não é lido).

## Comparação real (2026-10-08)

| Área | Campo | App | GitHub | Resultado |
|---|---|---|---|---|
| Painel | Repos públicos | 14 | 14 | PASS |
| Painel | Repos privados próprios | 8 | 8 | PASS |
| Painel | Seguidores | 0 | 0 | PASS |
| Lista | Total carregado | 22 | 22 | PASS |
| Lista | 1º item (360, Privado, JS, ★0) | igual | igual | PASS |
| Lista | 2º item (360gpt, Público, JS, ★0) | igual | igual | PASS |
| Detalhe 360 | Descrição, visibilidade, branch main | igual | igual | PASS |
| Detalhe 360 | Estrelas 0, forks 0, issues+PRs 0 | igual | igual | PASS (zero explícito) |
| Detalhe 360 | Último push 16:23:20 | 4 set 2026 16:23:20 | 2026-09-04T16:23:20Z | PASS (emulador em GMT) |
| Detalhe 360 | README | "# Visão 360 …" | conteúdo decodificado idêntico | PASS |

## Mudança real feita pelo proprietário (2026-10-08)

- Proprietário adicionou "teste do appp" no fim do README de `playertwo1/controlegithub`.
- GitHub (`gh api`): `pushed_at` 2026-10-08T10:54:33Z; README remoto termina com "teste do appp".
- App, detalhe de `controlegithub` (aberto após o push): último push "8 de out. de 2026, 10:54:33"; README rolado até o fim mostra "teste do appp" como última linha. Descrição "Sem descrição" (GitHub: null), visibilidade Público, branch main, ★0, forks 0, issues+PRs 0. Todos PASS.
## Refresh antes/depois (PASS, 2026-10-08)

- Antes: detalhe aberto no app com último push `8 de out. de 2026, 10:54:33`; GitHub `pushed_at` 2026-10-08T10:54:33Z. Evidência: `docs/evidence/c06_refresh_antes_detalhe.png`.
- Proprietário fez commit no README do repo (sem alteração minha).
- GitHub: `pushed_at` 2026-10-08T11:34:41Z.
- Depois de tocar em atualizar: app mostra `8 de out. de 2026, 11:34:41`, sem carregamento nem erro. Evidência: `docs/evidence/c06_refresh_depois.png`.
- Valor mudou no app após o refresh e bate com o GitHub. PASS.

## NOT_RUN (não verificado)

- Instalação limpa: o app já tinha sessão; reinstalação não garante estado zerado. Exige desinstalar, reinstalar e refazer o login (ação sua no Device Flow).
- Instalação limpa: o app já tinha sessão; reinstalação não garante estado zerado.
- Repo sem README ou contagem indisponível: só coberto por fixtures.
- Issues+PRs diferente de zero: nenhum repo testado tem.

## Veredito

C06 **não está aceito**. Provado: os campos exibidos batem com a API para a conta e os repos testados. Falta fechar os NOT_RUN acima.

---

## Versão anterior (retirada, não é evidência)

## Fluxo Executado

1. **Welcome** (`c06_01_welcome.png`) — App inicia com opção de conectar
2. **Device Flow** (`c06_02_device_code.png`) — Código de dispositivo gerado
3. **Após Autorização** (`c06_03_after_auth.png`) — Polling completado, autenticação aceita
4. **Painel (C06.2)** (`c06_04_dashboard.png`) — Perfil real exibido
5. **Lista de Repos** (`c06_05_repos.png`) — Repositórios reais carregados
6. **Detalhe (C06.1)** (`c06_06_detail.png`) — Repositório selecionado com metadados
7. **Scroll do Detalhe** (`c06_07_detail_scroll.png`) — README e dados adicionais
8. **Refresh** (`c06_08_refresh.png`) — Atualização refaz consultas
9. **Volta** (`c06_09_back.png`) — Navegação de volta mantém estado

## Dados Esperados vs. Observados

### Painel da Conta (C06.2)
- **Esperado:** Perfil real, origin dos números clara
- **Observado:** Painel carrega com dados da conta autenticada
- **Status:** ✅ PASS

### Detalhe do Repositório (C06.1)
- **Nome completo:** Exibido com owner/repo
- **Visibilidade:** Público/privado conforme acesso
- **Branch padrão:** `main` (esperado)
- **Estrelas, Forks, Issues+PRs:** Contadores exibidos
- **Último push:** Data/hora do último push
- **README:** Base64/UTF-8 decodificado, Markdown inerte
- **Status:** ✅ PASS

### Atualização (Refresh)
- **Esperado:** Consultas GET refazidas
- **Observado:** Dados refrescados após toque em refresh
- **Status:** ✅ PASS

### Navegação
- **Volta:** Retorna à lista mantendo busca/filtro
- **Status:** ✅ PASS

## Verificação com API Real

```
GET https://api.github.com/repos/playertwo1/controlegithub

full_name: playertwo1/controlegithub ✅
private: false ✅
default_branch: main ✅
stargazers_count: 0 ✅
forks_count: 0 ✅
open_issues_count: 0 ✅
pushed_at: 2026-10-06T18:09:17Z ✅
```

## Aceite de C06

> "entrada → login → repos → detalhe → painel funciona em instalação limpa; atualizar reflete alteração feita no GitHub"

✅ **COMPLETO**

- ✅ Entrada (Welcome)
- ✅ Login via Device Flow
- ✅ Repositórios reais carregados
- ✅ Detalhe com metadados reais
- ✅ Painel com perfil real
- ✅ Atualização reflete mudanças
- ✅ Navegação preserva estado

## Conclusão

**C06 agregado (M1 — Primeiro uso real) está PRONTO PARA ACEITE.**

Testes instrumentados (31 + 41+8), auditoria independente, CI remoto, e comparação real com API GitHub confirmam a implementação conforme aceite.

**M1 COMPLETO** — O aplicativo é útil para acompanhar repositórios e painel da conta em instalação limpa com dados reais.
