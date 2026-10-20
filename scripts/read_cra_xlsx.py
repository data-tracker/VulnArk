import sys
#!/usr/bin/env python3
"""手工解析 xlsx（openpyxl 兼容性问题），读取 Criteria 与 DAP847_CVE_Analysis_20260817"""
import zipfile, re
from xml.etree import ElementTree as ET

NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main',
      'r': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}

z = zipfile.ZipFile((sys.argv[1] if len(sys.argv) > 1 else 'Vulnerability Analysis CRA.xlsx'))

# shared strings
sst = []
try:
    root = ET.fromstring(z.read('xl/sharedStrings.xml'))
    for si in root.findall('m:si', NS):
        sst.append(''.join(t.text or '' for t in si.iter('{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t')))
except KeyError:
    pass

# workbook: sheet name -> file
wb = ET.fromstring(z.read('xl/workbook.xml'))
rels = ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
relmap = {rel.get('Id'): rel.get('Target') for rel in rels}
sheets = {}
for s in wb.iter('{http://schemas.openxmlformats.org/spreadsheetml/2006/main}sheet'):
    rid = s.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id')
    t = relmap.get(rid, '')
    if not t.startswith('xl/'): t = 'xl/' + t.lstrip('/')
    sheets[s.get('name')] = t

def read_sheet(path):
    root = ET.fromstring(z.read(path))
    rows = []
    for tr in root.iter('{http://schemas.openxmlformats.org/spreadsheetml/2006/main}row'):
        cells = {}
        for c in tr.findall('m:c', NS):
            ref = c.get('r') or ''
            col = re.match(r'([A-Z]+)', ref).group(1)
            t = c.get('t')
            v = c.find('m:v', NS)
            txt = ''
            if t == 's' and v is not None:
                txt = sst[int(v.text)]
            elif t == 'inlineStr':
                is_ = c.find('m:is', NS)
                txt = ''.join(x.text or '' for x in is_.iter('{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t')) if is_ is not None else ''
            elif v is not None:
                txt = v.text
            style = c.get('s')
            cells[col] = (txt, style)
        rows.append(cells)
    return rows

print('=== sheets ===', list(sheets))
print()
print('=== Criteria 内容 ===')
for row in read_sheet(sheets['Criteria']):
    line = [f"{col}={v[0]}" for col, v in sorted(row.items()) if v[0]]
    if line: print(' | '.join(line))
