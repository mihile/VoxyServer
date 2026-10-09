from pathlib import Path
import subprocess, threading, queue, time, shutil, re, sys
root=Path(__file__).resolve().parents[2]
base=root/'build/smoke-server'
clone=root/'build/lifecycle-server-b'
clone.mkdir(exist_ok=True)
if not (clone/'smoke-world').exists(): shutil.copytree(base/'smoke-world',clone/'smoke-world')
if not (clone/'libraries').exists(): shutil.copytree(base/'libraries',clone/'libraries')
for item in ['fabric-server-launch.jar','fabric-server-launcher.properties','server.jar','eula.txt','server.properties']:
    shutil.copy2(base/item,clone/item)
shutil.copytree(base/'mods',clone/'mods',dirs_exist_ok=True)
shutil.copytree(base/'config',clone/'config',dirs_exist_ok=True)
java=r'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin\java.exe'
events=queue.Queue(); processes=[]; logs=[]; ready=set(); client=None
def launch(name,args,cwd):
    p=subprocess.Popen(args,cwd=cwd,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
    f=open(root/f'build/lifecycle-{name}.log','w',encoding='utf-8'); logs.append(f); processes.append((name,p))
    def read():
        for line in p.stdout:
            f.write(line);f.flush();events.put((name,line))
    threading.Thread(target=read,daemon=True).start()
    return p
try:
    for name,port,extra in [('server-a',25585,[]),('server-b',25586,['--universe',str(clone),'--world','smoke-world'])]:
        launch(name,[java,'-Xmx2G','-jar','fabric-server-launch.jar','nogui','--port',str(port)]+extra,clone if name=='server-b' else base)
    deadline=time.monotonic()+420
    while time.monotonic()<deadline:
        try: name,line=events.get(timeout=.5)
        except queue.Empty: name,line='', ''
        if any(x in line.lower() for x in ['done (','lifecycle_test','initializing voxy','cache ready','error','exception','build failed','build successful','joined the game','shutting down remote','network handle']): print(name+': '+line.strip(),flush=True)
        if 'Done (' in line: ready.add(name)
        if 'joined the game' in line:
            for n,p in processes:
                if n==name:
                    p.stdin.write('op LifecycleTest\n');p.stdin.flush()
        if len(ready)==2 and client is None:
            if '--production' in sys.argv:
                game=Path((root/'build/production-client-directory.txt').read_text(encoding='utf-8'))
                client=launch('production-client',[java,'@'+str(root/'build/production-client.args')],game)
            else:
                client=launch('client',['cmd','/c','gradlew.bat','-I','verification/lifecycle/lifecycle.init.gradle','runClient','--console=plain'],root)
        if client is not None and client.poll() is not None: break
        if any(p.poll() is not None for n,p in processes if n.startswith('server')): break
    print('CLIENT_EXIT '+str(client.poll() if client else None),flush=True)
finally:
    for name,p in processes:
        if name.startswith('server') and p.poll() is None:
            p.stdin.write('stop\n');p.stdin.flush()
    for name,p in processes:
        if p.poll() is None:
            try:p.wait(timeout=20)
            except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=10)
    time.sleep(.3)
    for f in logs:f.close()
    print('FINAL_EXITS '+str([(n,p.returncode) for n,p in processes]),flush=True)
