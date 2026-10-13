#!/usr/bin/env python3
"""Explicitly clear application documents; keep the database, users and volume."""
import argparse,base64,urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
def env(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#'))
settings=env(root/'backend/.env');database=settings['BASEX_DATABASE']
parser=argparse.ArgumentParser(description='DESTRUCTIVE: remove all CloudMigration documents.')
parser.add_argument('--confirm',required=True,help='Repeat the exact configured database name.')
args=parser.parse_args()
if args.confirm!=database:parser.error('Confirmation must match the configured database name.')
admin=env(root/'infra/.env')['BASEX_PASSWORD']
query=ET.Element('query',xmlns='http://basex.org/rest')
ET.SubElement(query,'text').text='declare variable $db external; db:delete($db, "")'
ET.SubElement(query,'variable',name='db',value=database)
request=urllib.request.Request(settings['BASEX_URL'],data=ET.tostring(query),headers={'Content-Type':'application/xml','Authorization':'Basic '+base64.b64encode(('admin:'+admin).encode()).decode()})
with urllib.request.urlopen(request,timeout=15) as response:response.read()
print('Removed all documents from '+database+'. Database and credentials retained.')
