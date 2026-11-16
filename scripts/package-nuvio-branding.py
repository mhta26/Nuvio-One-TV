from pathlib import Path
import subprocess, shutil, tempfile
root=Path(__file__).resolve().parent.parent;master=root/'branding/nuvio-one-tv/logo-master.png';scratch_dir=tempfile.TemporaryDirectory(prefix='nuvio-one-tv-brand-');scratch=Path(scratch_dir.name)
font=root/'app/src/main/res/font/schibsted_grotesk_semibold.ttf'
def run(*args):subprocess.run(['magick',*map(str,args[:-1]),'-depth','8',str(args[-1])],check=True)
colors={'':'#0B1020','arctic_blue':'#0B1830','emerald':'#08251F','rose_gold':'#291824','copper':'#291B14','graphite':'#161C27'}
for suffix,bg in colors.items():
 key='_'+suffix if suffix else ''
 for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
  icon=scratch/f'icon{key}-{density}.png';run(master,'-resize',f'{size}x{size}','-background',bg,'-alpha','remove','-alpha','off','-strip',icon)
  plain=root/f'app/src/main/res/mipmap-{density}/ic_launcher{key}.png';plain.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(icon,plain)
  # Keep the existing optional TV badge distinct from the plain icon family.
  tv_icon=root/f'app/src/full/res/mipmap-{density}/iptv_ic_launcher{key}.png';tv_icon.parent.mkdir(parents=True,exist_ok=True)
  run(icon,'-font',font,'-fill','#0B1020','-draw',f'roundrectangle {size*0.61},{size*0.75} {size*0.94},{size*0.94} {size*0.04},{size*0.04}','-fill','#8BE6FF','-pointsize',str(round(size*0.12)),'-gravity','SouthEast','-annotate',f'+{round(size*0.10)}+{round(size*0.085)}','TV','-strip',tv_icon)
 mark=scratch/f'mark{key}.png';run(master,'-resize','150x150',mark)
 banner=scratch/f'banner{key}.png'
 run('-size','640x360',f'xc:{bg}',mark,'-gravity','North','-geometry','+0+34','-composite','-font',font,'-fill','#F4F7FF','-pointsize','43','-gravity','North','-annotate','+0+206','Nuvio One TV','-fill','#9BAEC9','-pointsize','17','-annotate','+0+270','YOUR WORLD. ONE SCREEN.','-resize','320x180','-strip',banner)
 shutil.copyfile(banner,root/f'app/src/main/res/mipmap-xhdpi/banner{key}.png')
 tv_banner=root/f'app/src/full/res/mipmap-xhdpi/iptv_banner{key}.png'
 run('-size','640x360',f'xc:{bg}',mark,'-gravity','North','-geometry','+0+34','-composite','-font',font,'-fill','#F4F7FF','-pointsize','43','-gravity','North','-annotate','+0+206','Nuvio One TV','-fill','#9BAEC9','-pointsize','17','-annotate','+0+270','STREAM. LIVE TV. MORE.','-resize','320x180','-strip',tv_banner)
# Small transparent assets keep the launch screen cheap to decode.
run(master,'-resize','256x256','-strip',root/'app/src/main/res/drawable-nodpi/app_logo_mark.png')
mark=scratch/'wordmark-symbol.png';run(master,'-resize','200x200',mark)
wordmark=scratch/'wordmark.png'
run('-size','1100x220','xc:none',mark,'-gravity','West','-geometry','+0+0','-composite','-font',font,'-fill','#F4F7FF','-pointsize','91','-gravity','West','-annotate','+218+0','Nuvio One TV','-strip',wordmark)
(root/'app/src/main/res/raw').mkdir(exist_ok=True)
shutil.copyfile(wordmark,root/'app/src/main/res/raw/one_tv_wordmark.png')
for p in (root/'app/src/main/res/drawable-nodpi').glob('app_logo_wordmark*.png'):shutil.copyfile(wordmark,p)
for p in (root/'app/src/main/res/drawable').glob('livetv_wordmark_iptv*.png'):shutil.copyfile(wordmark,p)
shutil.copyfile(root/'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',root/'app/src/main/res/drawable/ic_launcher.png')
shutil.copyfile(scratch/'banner.png',root/'branding/nuvio-one-tv/tv-banner.png')
print('Packaged launcher icons, TV banners, wordmarks and launch mark; original generated master preserved.')
