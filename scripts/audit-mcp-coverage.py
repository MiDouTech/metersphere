"""Static MCP inventory. Run from any directory; does not contact a platform.

This checks literal registrations in this repository, not deployed availability.
Semantic aliases are explicit: a name-only diff would overstate missing tools.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/task/agent_mcp_impl_check_fix_20260803/mcp-coverage-inventory-20260911.json'
ALIASES = dict(zip(
    ['add_project_members', 'search_bugs', 'get_bug', 'create_bug', 'update_bug',
     'relate_bug_case', 'create_functional_module', 'create_functional_case',
     'batch_create_functional_cases', 'create_project', 'get_functional_case',
     'list_modules', 'create_test_plan', 'associate_test_plan_cases',
     'create_case_review', 'associate_case_review_cases', 'search_functional_cases',
     'search_projects', 'submit_functional_result'],
    ['project.members.add', 'bug.search', 'bug.get', 'bug.create', 'bug.update',
     'bug.relate_case', 'functional.module.create', 'functional.case.create',
     'functional.case.batch_create', 'project.create', 'functional.get',
     'functional.modules', 'test_plan.create', 'test_plan.associate_cases',
     'case_review.create', 'case_review.associate_cases', 'functional.search',
     'project.search', 'functional.submit']))


def source(path, text, match):
    return {'file': path.relative_to(ROOT).as_posix(),
            'line': text[:match.start()].count('\n') + 1}


def main():
    native = {}
    handler_files = []
    for path in (ROOT / 'backend').rglob('*.java'):
        if '/src/main/' not in path.as_posix():
            continue
        text = path.read_text(encoding='utf-8-sig')
        if not re.search(r'implements AgentMcpToolHandler|new AgentMcpToolHandler|public AgentMcpToolHandler', text):
            continue
        handler_files.append(path.relative_to(ROOT).as_posix())
        pattern = (r'(?:return tool\(|terminalExecutionTool\()\s*"(metersphere\.[\w.]+)"'
                   if path.name == 'BuiltinAgentMcpToolConfig.java'
                   else r'return\s+"(metersphere\.[\w.]+)"')
        matches = list(re.finditer(pattern, text))
        assert matches, f'Unrecognized handler declaration: {path}'
        for match in matches:
            assert match[1] not in native, f'Duplicate tool: {match[1]}'
            native[match[1]] = source(path, text, match)
    index = (ROOT / 'metersphere-mcp/src/index.ts').read_text(encoding='utf-8-sig')
    registered = set(re.findall(r'\b(\w+Tool)\b', re.search(r'const tools: ToolDef\[\] = \[(.*?)\];', index, re.S)[1]))
    stdio = {}
    seen = set()
    for path in (ROOT / 'metersphere-mcp/src/tools').glob('*.ts'):
        text = path.read_text(encoding='utf-8-sig')
        for match in re.finditer(r'export const (\w+Tool) = \{\s*name:\s*"([^"]+)"', text):
            if match[1] in registered:
                seen.add(match[1])
                stdio[match[2]] = source(path, text, match)
    assert seen == registered, f'Unparsed stdio registrations: {registered - seen}'
    service = (ROOT / 'backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentMcpStreamableService.java').read_text(encoding='utf-8-sig')
    prefix = re.search(r'TRIGGER_TOOL_PREFIX = "([^"]+)"', service)[1]
    assert 'handler.name().startsWith(TRIGGER_TOOL_PREFIX)' in service
    assert 'MCP_TOOL_FORBIDDEN' in service
    blocked = sorted(name for name in native if name.startswith(prefix))
    exposed = set(native) - set(blocked)
    normalized = {name: 'metersphere.' + ALIASES[name] if name in ALIASES else name for name in stdio}
    endpoints = []
    for path in (ROOT / 'backend/services').rglob('*Controller.java'):
        if '/src/main/' not in path.as_posix():
            continue
        text = path.read_text(encoding='utf-8-sig')
        for match in re.finditer(r'@(Request|Get|Post|Put|Delete|Patch)Mapping\s*(?:\(([^\n]*)\))?', text):
            endpoints.append({**source(path, text, match), 'mapping': match[0]})
    result = {
        'kind': 'static source evidence; not a live tools/list or runtime test',
        'counts': {'native_registered': len(native), 'native_explicitly_blocked': len(blocked),
                   'native_potentially_visible_before_scope_filter': len(exposed), 'stdio_registered': len(stdio),
                   'common_semantic_names': len(exposed & set(normalized.values()))},
        'handler_files': sorted(handler_files), 'native': dict(sorted(native.items())),
        'blocked_by_design': blocked, 'stdio': dict(sorted(stdio.items())),
        'stdio_semantic_mapping': normalized,
        'native_missing_in_stdio': sorted(exposed - set(normalized.values())),
        'stdio_missing_native_direct_equivalent': sorted(name for name, canonical in normalized.items() if canonical not in exposed),
        'rest_mapping_inventory': endpoints,
        'limitations': ['Literal source parser; annotations and deployed bean loading require runtime verification.',
                       'Aliases equate business operations, not argument/error/idempotency contracts.',
                       'REST inventory is supporting evidence, not a rule that every REST endpoint needs MCP.']}
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result['counts'], ensure_ascii=False))
    print('native_missing_in_stdio:', len(result['native_missing_in_stdio']))
    print('stdio_missing_native_direct_equivalent:', result['stdio_missing_native_direct_equivalent'])
    print(OUT.relative_to(ROOT).as_posix())


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', help='Inventory path relative to repository root; default preserves the original audit name.')
    args = parser.parse_args()
    if args.output:
        OUT = ROOT / args.output
    ALIASES['submit_functional_results_batch'] = 'functional.submit.batch'
    main()
