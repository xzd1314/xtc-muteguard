import sys
import zipfile

base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]

with zipfile.ZipFile(base, 'r') as zin:
    names = zin.namelist()

with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
    with zipfile.ZipFile(base, 'r') as zin:
        for item in zin.infolist():
            data = zin.read(item.filename)
            # 压缩时不改 dex/already-compressed
            zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = item.external_attr
            zout.writestr(zi, data)
    with open(dex, 'rb') as f:
        zout.writestr('classes.dex', f.read())

print("packed:", out)
