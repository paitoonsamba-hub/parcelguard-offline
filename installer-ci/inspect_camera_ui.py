"""Inspect only webcam-related HTML/JS from the extracted verified PC Station archive.
No environment, server, token or personal-data output. ASCII-safe for Windows CI.
"""
from pathlib import Path
import re
for name in ("index.html","app.js"):
    p=Path("pc-installer/web")/name
    s=p.read_text(encoding="utf-8")
    lines=s.splitlines()
    terms=("getusermedia","enumeratedevices","videoinput","webcam","camera","deviceid","เปิดกล้อง","กล้องเว็บแคม","cam-start","cam-stop","video.")
    found=[i for i,line in enumerate(lines) if any(k in line.lower() for k in terms)]
    sections=set()
    for i in found:
        sections.update(range(max(0,i-3),min(len(lines),i+7)))
    print("=== Webcam UI excerpts",name,"lines",len(lines),"matches",len(found),"===")
    for i in sorted(sections):
        line=lines[i]
        if len(line)>1200:line=line[:1200]+"...[truncated]"
        print(str(i+1)+": "+ascii(line))
