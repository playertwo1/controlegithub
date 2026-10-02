"""Validate the small execution backlog using only Python's standard library."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def validate(data, root=ROOT):
    assert data['version'] == 1, 'Unsupported version'
    tasks = data['tasks']
    ids = [t['id'] for t in tasks]
    assert len(ids) == len(set(ids)), 'Duplicate task ID'
    by_id = {t['id']: t for t in tasks}
    assert sum(t['status'] == 'IN_PROGRESS' for t in tasks) <= 1, 'Multiple active tasks'
    assert {t['checkpoint'] for t in tasks} == {f'C{i:02}' for i in range(21)}, 'Checkpoint coverage'
    for task in tasks:
        ident = task['id']
        assert re.fullmatch(r'C\d{2}\.\d+', ident), f'{ident}: invalid ID'
        assert task['checkpoint'] == ident.split('.')[0], f'{ident}: checkpoint mismatch'
        assert task['title'].strip() and task['acceptance'].strip(), f'{ident}: missing outcome'
        assert task['status'] in {'PLANNED', 'READY', 'IN_PROGRESS', 'BLOCKED', 'DONE'}, f'{ident}: status'
        deps = task['depends_on']
        assert len(deps) == len(set(deps)), f'{ident}: duplicate dependencies'
        assert all(d in by_id and d != ident for d in deps), f'{ident}: invalid dependency'
        for key in ('contract', 'evidence'):
            if task[key]:
                path = (root / task[key]).resolve()
                assert path.is_relative_to(root.resolve()) and path.is_file(), f'{ident}: missing {key}'
        if task['status'] in {'READY', 'IN_PROGRESS'}:
            assert task['contract'], f'{ident}: contract required'
            text = (root / task['contract']).read_text(encoding='utf-8')
            for heading in ('Escopo', 'Pré-condições e decisões', 'Aceite', 'Verificação', 'Encerramento'):
                assert f'## {heading}' in text, f'{ident}: missing contract section {heading}'
        if task['status'] in {'IN_PROGRESS', 'DONE'}:
            assert all(by_id[d]['status'] == 'DONE' for d in deps), f'{ident}: unfinished prerequisite'
        if task['status'] == 'DONE':
            assert task['evidence'], f'{ident}: evidence required'
        if task['status'] == 'BLOCKED':
            assert task['blocked_reason'], f'{ident}: reason required'
    visited, visiting = set(), set()

    def walk(ident):
        assert ident not in visiting, f'{ident}: dependency cycle'
        if ident in visited:
            return
        visiting.add(ident)
        for dep in by_id[ident]['depends_on']:
            walk(dep)
        visiting.remove(ident)
        visited.add(ident)

    walk('C20.2')
    assert visited == set(ids), f'Tasks outside final delivery: {sorted(set(ids) - visited)}'
    eligible = [t['id'] for t in tasks if t['status'] == 'READY'
                and all(by_id[d]['status'] == 'DONE' for d in t['depends_on'])]
    return eligible


if __name__ == '__main__':
    try:
        plan = json.loads((ROOT / 'plan/tasks.json').read_text(encoding='utf-8'))
        eligible = validate(plan)
    except (AssertionError, KeyError, ValueError) as error:
        raise SystemExit(f'FAIL: {error}')
    print(f"PASS: {len(plan['tasks'])} tasks; eligible: {', '.join(eligible) or 'none'}")
