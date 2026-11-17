from pathlib import Path
import re
for p in Path("pc-installer").rglob("*"):
 if not p.is_file() or p.suffix not in (".py",".js",".html",".css"):continue
 s=p.read_text(encoding="utf-8",errors="replace")
 matches=[(i,line) for i,line in enumerate(s.splitlines(),1) if re.search(r'logout|log.out|ออกจากระบบ|position:\s*fixed|8765',line,re.I)]
 if matches:
  print("SOURCE",p,"size",len(s),"matches",len(matches))
  for i,line in matches[:30]: print("line",i,repr(line[:650]))
