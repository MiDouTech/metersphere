from pathlib import Path
source=Path('C:/Users/midoo/AppData/Local/Temp/mcp-rbac-mysql-run.py').read_text(encoding='utf-8')
source=source.replace("ROOT=pathlib.Path('C:/SoftWare/JetBrains/metersphere')", "ROOT=pathlib.Path('C:/Users/midoo/.codex/worktrees/msp38-integration/metersphere')")
target=Path('C:/Users/midoo/AppData/Local/Temp/msp38-regression-native-20261126.py')
target.write_text(source,encoding='utf-8')
print(target)
