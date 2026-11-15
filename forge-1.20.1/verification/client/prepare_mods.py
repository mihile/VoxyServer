"""Restore Forge jar-in-jar metadata stripped from Loom's named development copies."""
from hashlib import sha256
from pathlib import Path
import sys
import zipfile

root = Path(__file__).resolve().parent
sources = [
    root / '../../build/libs/VoxyServer-Forge-1.2.4+1.20.1-forge.2-client.jar',
    root / '../../../../neovoxy/editions/forge-1.20.1-client/build/libs/neo-voxy-0.4.0beta-mc1.20.1-forge-client.jar',
    Path(sys.argv[1]),
]
target = root / 'build/game/mods'
target.mkdir(parents=True, exist_ok=True)
for index, source in enumerate(sources):
    source = source.resolve(strict=True)
    digest = sha256(source.read_bytes()).hexdigest()[:12]
    matches = list((root / '.gradle/loom-cache/remapped_mods').rglob(f'*-{digest}.jar'))
    if len(matches) != 1:
        raise RuntimeError(f'Expected one remapped copy for {source}, found {matches}')
    destination = target / f'verification-{index}.jar'
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(matches[0]) as named, \
            zipfile.ZipFile(destination, 'w') as output:
        for entry in named.infolist():
            if entry.filename != 'META-INF/jarjar/metadata.json':
                output.writestr(entry, named.read(entry.filename))
        if 'META-INF/jarjar/metadata.json' in original.namelist():
            output.writestr('META-INF/jarjar/metadata.json', original.read('META-INF/jarjar/metadata.json'))
    print('Prepared', source.name, 'for the isolated client')
