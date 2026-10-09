#!/usr/bin/env python3
"""从 fonts.gstatic.com 下载 Material Symbols Rounded 图标并转换为可粘贴进
app/src/main/java/com/github/zzwtsy/easytierkt/ui/icons/AppIcons.kt 的 Kotlin 代码。

用法：
    python3 tools/convert-material-symbols.py <icon_name> [<icon_name> ...]

示例：
    python3 tools/convert-material-symbols.py pause play_arrow

SVG 采用字体坐标系（viewBox 0 -960 960 960），脚本把绝对 Y 坐标 +960 平移到
Compose 可用的正 Y 视口；相对命令（小写）是增量，不受影响。
"""

import re
import sys
import urllib.request

# 每个命令的参数个数，以及其中是 Y 坐标的下标（仅绝对命令需要平移）
COMMAND_PARAMS = {
    'M': (2, [1]), 'L': (2, [1]), 'T': (2, [1]),
    'H': (1, []), 'V': (1, [0]),
    'C': (6, [1, 3, 5]), 'S': (4, [1, 3]), 'Q': (4, [1, 3]),
    'A': (7, [6]), 'Z': (0, []),
}

TOKEN_RE = re.compile(r'([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)')
D_RE = re.compile(r'<path d="([^"]+)"')

SVG_URL = 'https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/{name}/default/24px.svg'


def fmt(v: float) -> str:
    if v == int(v):
        return str(int(v))
    return ('%f' % v).rstrip('0').rstrip('.')


def shift_path(d: str, dy: float = 960.0) -> str:
    tokens = TOKEN_RE.findall(d)
    seq = []
    cur_cmd = None
    cur_nums = []
    for letter, num in tokens:
        if letter:
            if cur_cmd is not None:
                seq.append((cur_cmd, cur_nums))
            cur_cmd = letter
            cur_nums = []
        else:
            cur_nums.append(num)
    if cur_cmd is not None:
        seq.append((cur_cmd, cur_nums))

    pieces = []
    for cmd, nums in seq:
        u = cmd.upper()
        absolute = cmd.isupper()
        arity, yidx = COMMAND_PARAMS[u]
        if arity == 0:
            pieces.append(cmd)
            continue
        if len(nums) % arity != 0:
            raise ValueError(f'{cmd} 参数个数 {len(nums)} 不是 {arity} 的倍数')
        groups = []
        for g in range(len(nums) // arity):
            vals = [float(x) for x in nums[g * arity:(g + 1) * arity]]
            if absolute:
                for yi in yidx:
                    vals[yi] += dy
            groups.append(' '.join(fmt(v) for v in vals))
        pieces.append(cmd + ' ' + ' '.join(groups))
    return ' '.join(pieces)


def camel_case(icon_name: str) -> str:
    return ''.join(part.capitalize() for part in icon_name.split('_')) + 'Icon'


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1

    for icon_name in sys.argv[1:]:
        url = SVG_URL.format(name=icon_name)
        with urllib.request.urlopen(url) as response:
            svg = response.read().decode('utf-8')
        match = D_RE.search(svg)
        if not match:
            print(f'!! {icon_name}: SVG 中没有 path', file=sys.stderr)
            return 1

        val_name = camel_case(icon_name)
        print(f'/** {icon_name}（用途待补充）。 */')
        print(f'val {val_name}: ImageVector by lazy {{')
        print(f'    materialSymbol(')
        print(f'        name = "{val_name}",')
        print(f'        pathData =')
        print(f'            "{shift_path(match.group(1))}",')
        print(f'    )')
        print(f'}}')
        print()
    return 0


if __name__ == '__main__':
    sys.exit(main())
