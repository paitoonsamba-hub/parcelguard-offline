/* Browser port of Android TrackingSelector.java. Keep the same conservative policy. */
(function(root){
'use strict';
function norm(s){return String(s||'').trim().toUpperCase();}
function lexBag(c){return ['LEX','FLASH','LEX+FLASH','LEX-ALL'].includes(norm(c).replace(/\s/g,''));}
function lexFlash(s){if(/^TH[0-9]{5}AU[A-Z0-9]{5}$/.test(s))return true;if(!/^TH[0-9]{5}[A-Z][A-Z0-9]{6}$/.test(s))return false;return (s.slice(7).match(/[A-Z]/g)||[]).length>=2;}
function thaiPostShape(s){return /^[A-Z]{2}[0-9]{9}TH$/.test(s);}
function thaiPost(s){if(!thaiPostShape(s))return false;const w=[8,6,4,2,3,5,9,7];let total=w.reduce((v,n,i)=>v+Number(s[2+i])*n,0),check=11-total%11;check=check===10?0:check===11?5:check;return check===Number(s[10]);}
function kexVerified(s){return /^KEXDOLM[0-9]{9}$/.test(s)||/^KEXLM[0-9]{10}$/.test(s);}
function kexUnknown(s){return /^KEX[A-Z0-9]{8,23}$/.test(s)&&!kexVerified(s);}
function known(s){if(/^LEX[A-Z0-9]{8,23}$/.test(s)||kexVerified(s)||kexUnknown(s))return 'LEX';if(/^SPX[A-Z0-9]{8,23}$/.test(s))return 'SPX';if(/^JT[A-Z0-9]{9,22}$/.test(s))return 'J&T';if(/^FLE[A-Z0-9]{8,22}$/.test(s))return 'Flash';return '';}
function carrierMatches(d,c){return lexBag(c)&&(d==='LEX'||d==='Flash')||norm(d)===norm(c);}
function valid(s){return /^[A-Z0-9]{9,32}$/.test(s)&&!s.includes('-')&&!s.includes('_');}
function score(raw,carrier){const s=norm(raw),c=norm(carrier).replace(/\s/g,'');if(!valid(s))return 0;
 if(lexFlash(s))return lexBag(c)?100:0;
 if(thaiPostShape(s))return lexBag(c)?(thaiPost(s)?100:40):0;
 const k=known(s);if(k){if(!carrierMatches(k,c))return 0;return kexUnknown(s)?40:100;}
 if(c==='SPX')return /^TH[A-Z0-9]{10,16}$/.test(s)?80:40;
 if(['LEX','LEX+FLASH','LEX-ALL','FLASH'].includes(c))return 40;
 if(c==='J&T')return /^[0-9]{12,16}$/.test(s)?85:/^TH[A-Z0-9]{10,16}$/.test(s)?75:40;
 return 40;
}
function choose(raws,carrier){
 const scores=new Map(),mismatched=[],seen=new Set();let sorting=false;
 for(const raw of (raws||[])){const s=norm(raw);if(!s||seen.has(s))continue;seen.add(s);
  if(!valid(s)){sorting=true;continue;}
  if(lexFlash(s)){if(lexBag(carrier))scores.set(s,100);else if(!mismatched.includes('LEX/Flash'))mismatched.push('LEX/Flash');continue;}
  if(thaiPostShape(s)){if(lexBag(carrier))scores.set(s,thaiPost(s)?100:40);else if(!mismatched.includes('LEX/Thailand Post'))mismatched.push('LEX/Thailand Post');continue;}
  const k=known(s);if(k&&!carrierMatches(k,carrier)){if(!mismatched.includes(k))mismatched.push(k);continue;}
  const n=score(s,carrier);if(n)scores.set(s,Math.max(scores.get(s)||0,n));
 }
 const strong=[...scores].filter(([,v])=>v>=70).map(([k])=>k),review=[...scores].filter(([,v])=>v<70).map(([k])=>k);
 if(strong.length===1)return mismatched.length?{autoTracking:'',reviewOptions:strong,message:'พบ Tracking ต่างขนส่งในภาพเดียวกัน • ตรวจใบปะหน้า'}:{autoTracking:strong[0],reviewOptions:[],message:''};
 if(strong.length>1)return {autoTracking:'',reviewOptions:strong,message:'พบ Tracking หลายเลข • กรุณาเลือกด้วยตนเอง'};
 if(review.length)return {autoTracking:'',reviewOptions:review,message:'ไม่แน่ใจรูปแบบ Tracking • กรุณาตรวจเลขบนใบปะหน้า'};
 if(mismatched.length)return {autoTracking:'',reviewOptions:[],message:'พบเลขต่างขนส่ง '+mismatched.join(', ')+' • ตรวจขนส่งก่อนสแกน'};
 return {autoTracking:'',reviewOptions:[],message:sorting?'พบเพียงรหัสคัดแยก/รหัสย่อย • หันเลข Tracking จริงเข้ากล้อง':''};
}
function fastVerified(raw,c){const s=norm(raw);return lexBag(c)&&(lexFlash(s)||thaiPost(s)||kexVerified(s)||(/^LEX[A-Z0-9]{8,23}$/.test(s)&&s.length===15)||(/^FLE[A-Z0-9]{8,22}$/.test(s)&&s.length===15));}
root.ParcelTrackingSelector={choose,confidence:score,fastVerifiedLexFlash:fastVerified};
})(typeof globalThis!=='undefined'?globalThis:this);
