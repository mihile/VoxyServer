from pathlib import Path
import shutil,json,sys
from datetime import datetime
root=Path(__file__).resolve().parents[2]
prefix='lifecycle-stress-client-' if '--stress' in sys.argv else 'lifecycle-production-client-'
game=root/'build'/(prefix+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
mods=game/'mods';mods.mkdir(parents=True,exist_ok=True)
cache=Path.home()/'.gradle/caches'
def artifact(group,name,version):
    return next((cache/'modules-2/files-2.1'/group/name/version).rglob(f'{name}-{version}.jar'))
for src in [root/'libs/voxy.jar',root/'build/libs/VoxyServer-1.2.4+1.20.1-port.2.jar',
            artifact('net.fabricmc.fabric-api','fabric-api','0.92.6+1.20.1'),
            artifact('maven.modrinth','sodium','mc1.20.1-0.5.13-fabric')]:
    shutil.copy2(src,mods/src.name)
shutil.copy2(root/'build/lifecycle-production-client/mods/lifecycle-harness.jar',mods/'lifecycle-harness.jar')
cp=(root/'build/loom-cache/argFiles/runClient').read_text().splitlines()[1].strip('"').split(';')
cp=[p for p in cp if 'modules-2' in p and not any(x in p for x in ['maven.modrinth','fabric-api','fabric-loader','sponge-mixin','org.ow2.asm'])]
serverlibs=root/'build/smoke-server/libraries'
cp += [str(p) for dirname in ['net/fabricmc','org/ow2'] for p in (serverlibs/dirname).rglob('*.jar')]
mc=cache/'fabric-loom/1.20.1/minecraft-client.jar'
cp.append(str(mc))
cp=list(dict.fromkeys(cp))
args=['-Xmx2G','-Dfabric.development=false',f'-Dfabric.gameJarPath={mc.as_posix()}',
      '-cp',';'.join(cp).replace('\\','/'),'net.fabricmc.loader.impl.launch.knot.KnotClient',
      '--username','LifecycleTest','--version','1.20.1','--gameDir',game.as_posix(),
      '--assetsDir',(cache/'fabric-loom/assets').as_posix(),'--assetIndex','1.20.1-5',
      '--accessToken','0','--width','854','--height','480']
(root/'build/production-client.args').write_text('\n'.join('"'+a+'"' for a in args),encoding='utf-8')
(root/'build/production-client-directory.txt').write_text(str(game),encoding='utf-8')
print('PRODUCTION_MODS',sorted(p.name for p in mods.iterdir()))
