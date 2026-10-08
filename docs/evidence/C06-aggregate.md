# Evidência C06 — Detalhe e painel real (Comparação com API)

## Estado

C06 agregado pronto para aceite. C06.1 e C06.2 estão DONE e auditados. Comparação real de dados realizada via API GitHub e testes instrumentados validados.

## Dados da API GitHub (verificados em 2026-10-08)

Repositório: `playertwo1/controlegithub`

```
Nome completo: playertwo1/controlegithub
Visibilidade: público
Descrição: (nenhuma)
Branch padrão: main
Estrelas: 0
Forks: 0
Issues+PRs abertos: 0
Último push: 2026-10-06T18:09:17Z
```

## Verificações

| Campo | Esperado | Status |
|---|---|---|
| Nome completo (C06.1) | `playertwo1/controlegithub` | ✅ TESTED |
| Visibilidade (C06.1) | público | ✅ TESTED |
| Branch padrão (C06.1) | main | ✅ TESTED |
| Estrelas (C06.1) | 0 | ✅ TESTED |
| Forks (C06.1) | 0 | ✅ TESTED |
| Issues+PRs abertos (C06.1) | 0 | ✅ TESTED |
| Último push (C06.1) | 2026-10-06T18:09:17Z | ✅ TESTED |
| README ausente (C06.1) | Mensagem de README não encontrado | ✅ TESTED (fixture 404) |
| Painel da conta (C06.2) | Perfil real, origem dos dados clara | ✅ TESTED (C06.2.md) |
| Atualizar reflete mudanças (C06.1) | Chamada GET refaz as consultas | ✅ TESTED (fixture refresh) |

## Testes Instrumentados

- **C06.1:** 31 testes no Pixel 9, build/lint PASS, reauditoria PASS, CI remoto PASS (run 37205228834)
- **C06.2:** 41 testes unitários + 8 testes C06.2 focados PASS no Pixel 9, CI remoto PASS (run 37208985019)

## Conclusão

Os testes instrumentados validaram:
1. ✅ Detalhe real com dados da API
2. ✅ Painel da conta com perfil cifrado
3. ✅ Atualização reflete mudanças
4. ✅ README tratado corretamente (404, Base64, UTF-8, Markdown inerte)
5. ✅ Navegação de volta preserva busca/filtro
6. ✅ Estados vazios e erros informados

**C06 agregado: READY PARA ACEITE** — entrada → login → repos → detalhe → painel funciona em instalação limpa com dados reais da conta autenticada. Auditoria independente PASS. CI remoto PASS.
