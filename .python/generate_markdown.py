"""Generate localized changelogs from the existing JSON sources."""
import json
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[1]
    template = (root / '.changelog/template_changelog.md').read_text(encoding='utf-8')
    for source in sorted((root / '.changelog').glob('lang_*.json')):
        data = json.loads(source.read_text(encoding='utf-8'))
        lines = []
        for version, release in data['$data'].items():
            lines += [f'# {version}', '', f'###### {release["released_date"]}', '']
            for category in ('hint', 'feature', 'fix', 'improvement', 'dependency'):
                for entry in release.get(category, []):
                    lines.append(f'* `{data["changelog_label_" + category]}` {entry}')
            lines.append('')
        text = template.replace('{{ h3_release_history }}', data['h3_release_history'])
        text = text.replace('{{ placeholder_release_history }}', '\n'.join(lines).rstrip())
        destination = source.with_name('CHANGELOG-' + source.stem.removeprefix('lang_') + '.md')
        destination.write_text(text.rstrip() + '\n', encoding='utf-8')
        print(destination.relative_to(root))


if __name__ == '__main__':
    main()
