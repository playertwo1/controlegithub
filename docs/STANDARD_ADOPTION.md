# Adoção do Ideias Standard

Fonte: https://github.com/playertwo1/ideias_standard
Revisão: `f546a2a128956e87c7ae89c103ca67aba4054cc4`.
Base: `templates/gold`, perfil LIGHT, contexto progressivo.

O template original é Python. Este projeto adapta seus contratos para Android:
README, AGENTS, manifests, lock, comandos verificáveis e CI, com estado e roadmap.
O pack Python e seu código de exemplo não foram copiados porque não se aplicam.
Não há pack Android no projeto nem catálogo de Skills instalado por padrão.

Regras mantidas: NOT_RUN não é PASS; autoridade de produto é do usuário;
mudanças pequenas; dados e evidências honestos; auditoria independente pelo delta.

O lock registra a revisão de origem e os desvios locais. Não afirma sincronização
automática nem certificação Gold. Auditoria independente é registrada separadamente.

Para verificar com o repositório Standard clonado ao lado:

Use um ambiente Python com `PyYAML==6.0.3` e `jsonschema==4.26.0` instalados.

```sh
python ../ideias_standard/scripts/check.py . --details
python ../ideias_standard/scripts/check.py . --kind standard-lock --details
python ../ideias_standard/scripts/check.py . --kind context-manifest --details
```
