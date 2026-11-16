from pathlib import Path
for name in ("index.html","app.js"):
 lines=(Path("pc-installer/web")/name).read_text(encoding="utf-8").splitlines()
 print("DIAGNOSTIC",name,"total",len(lines))
 for i,line in enumerate(lines):
  if name=="index.html" and 0<=i<66:
   print(i+1,ascii(line[:1100]))
  if name=="app.js" and (i<60 or "page" in line.lower() and ("active" in line or "nav" in line or "show" in line or "scan" in line)):
   print(i+1,ascii(line[:900]))
