"""Validate the small execution backlog using only Python's standard library."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    """Raise a validation error regardless of Python optimization settings."""
    if not condition:
        raise ValueError(message)


def validate(data, root=ROOT):
    require(data['version'] == 1, 'Unsupported version')
    tasks = data['tasks']
    ids = [t['id'] for t in tasks]
    require(len(ids) == len(set(ids)), 'Duplicate task ID')
    by_id = {t['id']: t for t in tasks}
    require(sum(t['status'] == 'IN_PROGRESS' for t in tasks) <= 1, 'Multiple active tasks')
    require({t['checkpoint'] for t in tasks} == {f'C{i:02}' for i in range(21)}, 'Checkpoint coverage')
    for task in tasks:
        ident = task['id']
        require(re.fullmatch(r'C\d{2}\.\d+', ident), f'{ident}: invalid ID')
        require(task['checkpoint'] == ident.split('.')[0], f'{ident}: checkpoint mismatch')
        require(task['title'].strip() and task['acceptance'].strip(), f'{ident}: missing outcome')
        require(task['status'] in {'PLANNED', 'READY', 'IN_PROGRESS', 'BLOCKED', 'DONE'}, f'{ident}: status')
        deps = task['depends_on']
        require(len(deps) == len(set(deps)), f'{ident}: duplicate dependencies')
        require(all(d in by_id and d != ident for d in deps), f'{ident}: invalid dependency')
        for key in ('contract', 'evidence'):
            if task[key]:
                path = (root / task[key]).resolve()
                require(path.is_relative_to(root.resolve()) and path.is_file(), f'{ident}: missing {key}')
        if task['status'] in {'READY', 'IN_PROGRESS'}:
            require(task['contract'], f'{ident}: contract required')
            text = (root / task['contract']).read_text(encoding='utf-8')
            for heading in ('Escopo', 'Pré-condições e decisões', 'Aceite', 'Verificação', 'Encerramento'):
                require(f'## {heading}' in text, f'{ident}: missing contract section {heading}')
        if task['status'] in {'IN_PROGRESS', 'DONE'}:
            require(all(by_id[d]['status'] == 'DONE' for d in deps), f'{ident}: unfinished prerequisite')
        if task['status'] == 'DONE':
            require(task['evidence'], f'{ident}: evidence required')
        if task['status'] == 'BLOCKED':
            require(task['blocked_reason'], f'{ident}: reason required')
    visited, visiting = set(), set()

    def walk(ident):
        require(ident not in visiting, f'{ident}: dependency cycle')
        if ident in visited:
            return
        visiting.add(ident)
        for dep in by_id[ident]['depends_on']:
            walk(dep)
        visiting.remove(ident)
        visited.add(ident)

    walk('C20.2')
    require(visited == set(ids), f'Tasks outside final delivery: {sorted(set(ids) - visited)}')
    eligible = [t['id'] for t in tasks if t['status'] == 'READY'
                and all(by_id[d]['status'] == 'DONE' for d in t['depends_on'])]
    return eligible


if __name__ == '__main__':
    try:
        plan = json.loads((ROOT / 'plan/tasks.json').read_text(encoding='utf-8'))
        eligible = validate(plan)
    except (KeyError, TypeError, ValueError, OSError) as error:
        raise SystemExit(f'FAIL: {error}')
    print(f"PASS: {len(plan['tasks'])} tasks; eligible: {', '.join(eligible) or 'none'}")
