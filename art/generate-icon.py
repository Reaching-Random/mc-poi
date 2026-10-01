import math, random
from PIL import Image
N=32
img=Image.new('RGBA',(N,N),(0,0,0,0)); px=img.load()
random.seed(7)
# --- map: parchment frame + terrain ---
FRAME_D=(92,66,36,255); FRAME=(201,170,112,255); FRAME_L=(226,202,150,255)
def h(x,y):
    return (math.sin(x*0.33+1.2)+math.sin(y*0.29+4.0)+math.sin((x+y)*0.21+2.0)+math.sin((x-y)*0.17))/4
for y in range(1,31):
    for x in range(1,31):
        corner=(x in(1,30) and y in(1,30))
        if corner: continue
        edge = x in(1,30) or y in(1,30)
        inner = 3<=x<=28 and 3<=y<=28
        if edge: px[x,y]=FRAME_D
        elif not inner: px[x,y]=FRAME_L if (x==2 or y==2) else FRAME
        else:
            v=h(x,y); r=random.random()
            if v<-0.22: c=(52,84,190) if r<0.5 else (62,98,208)      # deep water
            elif v<-0.08: c=(84,126,226) if r<0.6 else (98,140,236)  # shallow
            elif v<-0.02: c=(222,206,146) if r<0.7 else (208,190,128) # sand
            elif v<0.28: c=(110,160,62) if r<0.55 else (96,146,52)   # grass
            else: c=(58,108,40) if r<0.6 else (46,92,34)             # forest
            px[x,y]=c+(255,)
# --- pin ---
cx,cy,r,tip=15.5,12.5,3.8,21
def inside(x,y):
    dx=abs(x-cx)
    if y<=cy: return dx*dx+(y-cy)**2<=r*r
    t=(y-cy)/(tip-cy)
    if t>1: return False
    circ=math.sqrt(max(r*r-(y-cy)**2,0)) if (y-cy)<r else 0
    return dx<=max(circ, r*1.02*(1-t)+0.35)
mask={(x,y) for y in range(N) for x in range(N) if inside(x,y)}
ty=max(y for _,y in mask)
for (x,y) in [(14,ty+1),(15,ty+1),(16,ty+1),(17,ty+1),(18,ty+1),(16,ty+2),(17,ty+2)]:
    p=px[x,y]; px[x,y]=tuple(int(c*0.55) for c in p[:3])+(255,)
out={(x+dx,y+dy) for (x,y) in mask for dx in(-1,0,1) for dy in(-1,0,1)}-mask
for (x,y) in out: px[x,y]=(74,14,14,255)
for (x,y) in mask:
    c=(226,52,46)
    if x-cx>1.5 or (y>cy+2 and x>cx): c=(184,30,30)
    if (x-cx+1.5)**2+(y-cy+1.5)**2<2 : c=(255,120,104)
    d=(x-cx)**2+(y-cy)**2
    if d<=1.0: c=(250,244,232)
    px[x,y]=c+(255,)
img.save('icon-32.png')
img.resize((512,512),Image.NEAREST).save('icon-512.png',optimize=True)
