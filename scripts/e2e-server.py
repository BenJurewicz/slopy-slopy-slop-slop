#!/usr/bin/env python3
"""Reset only the isolated test database, then run the packaged app on port 8081."""
import base64, os, subprocess, sys, urllib.request
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
def env(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#'))
settings=env(ROOT/'backend/.env');admin=env(ROOT/'infra/.env')['BASEX_PASSWORD']
database=settings.get('BASEX_DATABASE','cloudmigration')+'_tests'
assert database.endswith('_tests') and database != settings.get('BASEX_DATABASE','cloudmigration')
root=ET.Element('query',xmlns='http://basex.org/rest')
ET.SubElement(root,'text').text='declare variable $db external; if(db:exists($db)) then db:delete($db, "") else db:create($db)'
ET.SubElement(root,'variable',name='db',value=database)
request=urllib.request.Request(settings['BASEX_URL'],data=ET.tostring(root),headers={'Content-Type':'application/xml','Authorization':'Basic '+base64.b64encode(('admin:'+admin).encode()).decode()})
with urllib.request.urlopen(request,timeout=15) as response:response.read()
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
if sys.platform=='darwin' and not os.environ.get('JAVA_HOME'):
    java=subprocess.check_output(['/usr/libexec/java_home','-v','23'],text=True).strip()+'/bin/java'
os.chdir(ROOT/'backend')
os.execv(java if '/' in java else __import__('shutil').which(java),[java,'-jar','build/libs/cloudmigration-backend-0.0.1-SNAPSHOT.jar','--server.port=8081','--basex.database='+database])
