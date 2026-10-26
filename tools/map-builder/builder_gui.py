"""Small native desktop UI. All map work runs in a separate cancellable process."""
import json
import os
from pathlib import Path
import queue
import signal
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog,messagebox,scrolledtext,ttk
import webbrowser

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]

class BuilderWindow:
    def __init__(self,window):
        self.window=window;window.title('Jeland Map Builder');window.geometry('960x780');window.minsize(790,660)
        self.process=None;self.events=queue.Queue();self.cancelled=False;self.mode='build'
        self.source=tk.StringVar();self.title=tk.StringVar();self.ident=tk.StringVar(value='my-region')
        self.output=tk.StringVar();self.source_url=tk.StringVar(value='https://download.geofabrik.de/')
        self.clip=tk.BooleanVar(value=False);self.threads=tk.IntVar(value=2)
        self.bounds=[tk.StringVar(value=x) for x in ('61.38','55.14','61.43','55.18')]
        self.status=tk.StringVar(value='Выберите исходный .osm.pbf и новую папку результата')
        outer=ttk.Frame(window,padding=18);outer.pack(fill='both',expand=True);outer.columnconfigure(1,weight=1)
        ttk.Label(outer,text='Подготовка карт для Jeland OS',font=('Segoe UI',18,'bold')).grid(row=0,column=0,columnspan=3,sticky='w',pady=(0,12))
        self.field(outer,1,'Исходный OSM-файл',self.source,self.pick_source)
        self.field(outer,2,'Название карты',self.title)
        self.field(outer,3,'ID / имя пакета',self.ident)
        ttk.Label(outer,text='Латинские строчные буквы, цифры и дефис. Например: chelyabinsk-center',foreground='#526477').grid(row=4,column=1,columnspan=2,sticky='w')
        self.field(outer,5,'Новая папка результата',self.output,self.pick_output)
        self.field(outer,6,'Ссылка на источник',self.source_url)
        options=ttk.LabelFrame(outer,text='Область карты',padding=12);options.grid(row=7,column=0,columnspan=3,sticky='ew',pady=12)
        ttk.Checkbutton(options,text='Обрезать по границам (иначе используется весь OSM-файл)',variable=self.clip,command=self.update_bounds).grid(row=0,column=0,columnspan=4,sticky='w',pady=(0,10))
        self.bound_widgets=[]
        for i,label in enumerate(('Запад · долгота','Юг · широта','Восток · долгота','Север · широта')):
            ttk.Label(options,text=label).grid(row=1,column=i,sticky='w',padx=6)
            field=ttk.Entry(options,textvariable=self.bounds[i],width=18);field.grid(row=2,column=i,sticky='ew',padx=6);self.bound_widgets.append(field)
            options.columnconfigure(i,weight=1)
        ttk.Label(options,text='Координаты — в десятичных градусах. Берите область с запасом вокруг нужных дорог.',foreground='#526477').grid(row=3,column=0,columnspan=4,sticky='w',pady=(8,0))
        actions=ttk.Frame(outer);actions.grid(row=8,column=0,columnspan=3,sticky='ew')
        self.build_button=ttk.Button(actions,text='Собрать карту',command=self.start_build);self.build_button.pack(side='left',padx=(0,8))
        self.cancel_button=ttk.Button(actions,text='Остановить',command=self.cancel,state='disabled');self.cancel_button.pack(side='left',padx=(0,8))
        self.validate_button=ttk.Button(actions,text='Проверить ZIP…',command=self.validate);self.validate_button.pack(side='left',padx=(0,8))
        ttk.Label(actions,text='Потоки:').pack(side='left',padx=(8,4));ttk.Spinbox(actions,from_=1,to=16,textvariable=self.threads,width=4).pack(side='left')
        ttk.Button(actions,text='Где взять OSM?',command=lambda:webbrowser.open('https://download.geofabrik.de/russia.html')).pack(side='right')
        config_actions=ttk.Frame(outer);config_actions.grid(row=9,column=0,columnspan=3,sticky='ew',pady=(10,0))
        ttk.Button(config_actions,text='Загрузить настройки…',command=self.load_config).pack(side='left',padx=(0,8))
        ttk.Button(config_actions,text='Сохранить настройки…',command=self.save_config).pack(side='left',padx=(0,8))
        ttk.Button(config_actions,text='Инструкция',command=self.instructions).pack(side='left')
        ttk.Button(config_actions,text='Открыть результат',command=self.open_output).pack(side='right')
        ttk.Label(outer,textvariable=self.status,wraplength=860).grid(row=10,column=0,columnspan=3,sticky='w',pady=(14,6))
        self.progress=ttk.Progressbar(outer,mode='indeterminate');self.progress.grid(row=11,column=0,columnspan=3,sticky='ew')
        self.log=scrolledtext.ScrolledText(outer,height=14,wrap='word',font=('Consolas',10));self.log.grid(row=12,column=0,columnspan=3,sticky='nsew',pady=(10,0));outer.rowconfigure(12,weight=1)
        self.update_bounds();window.protocol('WM_DELETE_WINDOW',self.close);window.after(150,self.poll)

    def field(self,parent,row,label,value,picker=None):
        ttk.Label(parent,text=label).grid(row=row,column=0,sticky='w',padx=(0,14),pady=5)
        ttk.Entry(parent,textvariable=value).grid(row=row,column=1,columnspan=1 if picker else 2,sticky='ew',pady=5)
        if picker:ttk.Button(parent,text='Выбрать…',command=picker).grid(row=row,column=2,padx=(8,0))

    def update_bounds(self):
        for widget in self.bound_widgets:widget.configure(state='normal' if self.clip.get() else'disabled')

    def pick_source(self):
        path=filedialog.askopenfilename(title='Исходный OSM-файл',filetypes=[('OpenStreetMap PBF','*.osm.pbf')])
        if path:self.source.set(path)

    def pick_output(self):
        parent=filedialog.askdirectory(title='Выберите родительскую папку для результата')
        if parent:self.output.set(str(Path(parent)/self.ident.get()))

    def config(self):
        if not self.source.get().strip() or not self.output.get().strip():raise ValueError('Выберите OSM-файл и новую папку результата')
        result=dict(id=self.ident.get().strip(),title=self.title.get().strip(),source=str(Path(self.source.get()).resolve()),
                    output=str(Path(self.output.get()).resolve()),source_url=self.source_url.get().strip(),threads=self.threads.get(),min_zoom=10,max_zoom=15)
        if self.clip.get():result['bounds']=[float(value.get().replace(',','.')) for value in self.bounds]
        return result

    def save_config(self):
        try:
            value=self.config();path=filedialog.asksaveasfilename(title='Настройки региона',defaultextension='.json',filetypes=[('JSON','*.json')])
            if path:Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
        except Exception as error:messagebox.showerror('Настройки',str(error))

    def load_config(self):
        path=filedialog.askopenfilename(filetypes=[('JSON','*.json')])
        if not path:return
        try:
            data=json.loads(Path(path).read_text('utf-8-sig'))
            for key,var in (('id',self.ident),('title',self.title),('source_url',self.source_url)):var.set(data.get(key,''))
            for key,var in (('source',self.source),('output',self.output)):
                value=Path(data[key]);var.set(str(value if value.is_absolute() else (Path(path).parent/value).resolve()))
            self.threads.set(data.get('threads',2));self.clip.set(bool(data.get('bounds')))
            for value,field in zip(data.get('bounds',[]),self.bounds):field.set(str(value))
            self.update_bounds()
        except Exception as error:messagebox.showerror('Настройки',str(error))

    def start_build(self):
        try:
            from map_builder import read_config
            config=self.config();temp=ROOT/'.local/map-builder-requests';temp.mkdir(parents=True,exist_ok=True)
            import uuid
            request=temp/(str(uuid.uuid4())+'.json');request.write_text(json.dumps(config,ensure_ascii=False),encoding='utf-8')
            read_config(request)
            self.start(['build','--config',str(request)],'build')
        except Exception as error:messagebox.showerror('Не удалось начать сборку',str(error))

    def validate(self):
        path=filedialog.askopenfilename(title='Проверить готовый пакет',filetypes=[('Jeland navigation','*.jelandnav.zip'),('ZIP','*.zip')])
        if path:self.start(['validate',path],'validate')

    def start(self,args,mode):
        if self.process:return
        self.mode=mode;self.cancelled=False;self.log.delete('1.0','end');self.progress.start()
        for button in (self.build_button,self.validate_button):button.configure(state='disabled')
        self.cancel_button.configure(state='normal');self.status.set('Сборка началась…' if mode=='build' else'Проверка пакета…')
        flags={'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {'start_new_session':True}
        self.process=subprocess.Popen([sys.executable,str(HERE/'map_builder.py'),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                                      text=True,encoding='utf-8',errors='replace',env=dict(os.environ,PYTHONIOENCODING='utf-8'),**flags)
        process=self.process
        def reader():
            for line in process.stdout:self.events.put(('line',line))
            self.events.put(('exit',process.wait()))
        threading.Thread(target=reader,daemon=True).start()

    def poll(self):
        try:
            while True:
                kind,value=self.events.get_nowait()
                if kind=='line':
                    self.log.insert('end',value);self.log.see('end')
                    if '/6 ' in value or value.startswith('ГОТОВО:'):self.status.set(value.strip())
                else:
                    self.process=None;self.progress.stop();self.cancel_button.configure(state='disabled')
                    for button in (self.build_button,self.validate_button):button.configure(state='normal')
                    self.status.set('Остановлено. Для новой попытки выберите новую папку.' if self.cancelled else'Готово' if value==0 else'Ошибка — подробности в журнале')
                    if value and not self.cancelled:messagebox.showerror('Сборка не завершена','Причина указана в журнале. Неполный результат не публикуйте.')
        except queue.Empty:pass
        self.window.after(150,self.poll)

    def cancel(self):
        if self.process and self.process.poll() is None:
            self.cancelled=True
            if os.name=='nt':
                subprocess.run(['taskkill','/PID',str(self.process.pid),'/T','/F'],creationflags=subprocess.CREATE_NO_WINDOW,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            else:os.killpg(self.process.pid,signal.SIGTERM)

    def close(self):
        if self.process:
            if not messagebox.askyesno('Завершить?','Остановить текущую сборку и закрыть окно?'):return
            self.cancel()
        self.window.destroy()

    def open_output(self):
        path=Path(self.output.get())
        if self.output.get().strip() and path.is_dir():
            if os.name=='nt':os.startfile(path)
            else:webbrowser.open(path.resolve().as_uri())

    def instructions(self):
        path=ROOT/'docs/MAP-BUILDER.md'
        if path.exists():
            if os.name=='nt':subprocess.Popen(['notepad.exe',str(path)])
            else:webbrowser.open(path.resolve().as_uri())

def launch():
    root=tk.Tk();BuilderWindow(root);root.mainloop()

if __name__=='__main__':launch()
