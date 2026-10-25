package ru.jeland.os.navhost;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.view.*;
import java.lang.reflect.Method;

/** Explicitly started by ADB as shell, never root. No socket, command interpreter or general IPC. */
public final class NavHostShell extends Binder {
    public static final String DESCRIPTOR="ru.jeland.os.navhost.v1";
    public static final int PING=1,ATTACH=2,DETACH=3,TOUCH=4,BACK=5,STOP=6,REOPEN=7;
    private static final String[] PACKAGES={"ru.dublgis.dgismobile","ru.yandex.yandexnavi"};
    private final Context context;
    private final int appUid;
    private VirtualDisplay display;
    private Surface surface;
    private String provider;
    private int width,height;
    private int inputs;
    private final Object inputManager;
    private final Method inject,setDisplay;

    private NavHostShell(Context context,int appUid) throws Exception {
        this.context=context;this.appUid=appUid;
        Class<?> manager=Class.forName("android.hardware.input.InputManager");
        inputManager=manager.getMethod("getInstance").invoke(null);
        inject=manager.getMethod("injectInputEvent",InputEvent.class,int.class);
        setDisplay=InputEvent.class.getMethod("setDisplayId",int.class);
    }
    public static void main(String[] args) throws Exception {
        if(Process.myUid()!=2000)throw new SecurityException("ADB shell only");
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Object thread=activityThread.getMethod("systemMain").invoke(null);
        Context system=(Context)activityThread.getMethod("getSystemContext").invoke(thread);
        Context shell=system.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY);
        java.lang.reflect.Field op=shell.getClass().getDeclaredField("mOpPackageName");op.setAccessible(true);op.set(shell,"com.android.shell");
        int uid=shell.getPackageManager().getApplicationInfo("ru.jeland.os",0).uid;
        NavHostShell host=new NavHostShell(shell,uid);
        Bundle data=new Bundle();data.putBinder("host",host);data.putInt("pid",Process.myPid());
        Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class<?> iam=Class.forName("android.app.IActivityManager");
        IBinder providerToken=new Binder();
        Object holder=iam.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class)
                .invoke(am,"ru.jeland.os.navhost",0,providerToken,"JelandNavigation");
        if(holder==null)throw new IllegalStateException("Jeland OS provider unavailable");
        Object provider=holder.getClass().getField("provider").get(holder);
        Bundle response=null;
        for(Method method:Class.forName("android.content.IContentProvider").getMethods())if(method.getName().equals("call")) {
            if(method.getParameterTypes().length==6)response=(Bundle)method.invoke(provider,"com.android.shell",null,"ru.jeland.os.navhost","register",null,data);
            else if(method.getParameterTypes().length==5)response=(Bundle)method.invoke(provider,"com.android.shell","ru.jeland.os.navhost","register",null,data);
            else continue;
            break;
        }
        iam.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(am,"ru.jeland.os.navhost",providerToken);
        if(response==null||!response.getBoolean("accepted"))throw new IllegalStateException("Registration rejected; stop old helper first");
        IBinder client=response.getBinder("client");
        if(client==null)throw new IllegalStateException("Missing client lifetime");
        client.linkToDeath(()->{host.closeDisplay();System.exit(0);},0);
        System.out.println("Jeland navigation helper ready; uid=2000; only two navigators allowed");
        Looper.loop();
    }
    @Override protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags) {
        if(Binder.getCallingUid()!=appUid)throw new SecurityException("Jeland OS UID required");
        data.enforceInterface(DESCRIPTOR);
        long identity=Binder.clearCallingIdentity();
        try {
            switch(code) {
                case PING:break;
                case ATTACH: {
                    int p=data.readInt();int w=data.readInt(),h=data.readInt(),dpi=data.readInt();
                    Surface next=Surface.CREATOR.createFromParcel(data);
                    if(p<0||p>=PACKAGES.length||w<64||h<64||w>4096||h>4096||dpi<80||dpi>640||!next.isValid()) {
                        next.release();throw new IllegalArgumentException("Invalid navigation surface");
                    }
                    attach(PACKAGES[p],next,w,h,dpi);break;
                }
                case DETACH:
                    if(display!=null)display.setSurface(null);
                    if(surface!=null){surface.release();surface=null;}
                    break;
                case TOUCH: {
                    MotionEvent event=MotionEvent.CREATOR.createFromParcel(data);
                    try {if(display!=null&&surface!=null&&event.getPointerCount()<=2) {
                        int action=event.getActionMasked();
                        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP) {
                            int count=event.getPointerCount();boolean valid=count>0&&event.getActionIndex()<count;
                            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
                            MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[count];
                            for(int i=0;i<count;i++) {
                                properties[i]=new MotionEvent.PointerProperties();event.getPointerProperties(i,properties[i]);
                                properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
                                coordinates[i]=new MotionEvent.PointerCoords();event.getPointerCoords(i,coordinates[i]);
                                valid&=Float.isFinite(coordinates[i].x)&&Float.isFinite(coordinates[i].y)&&properties[i].id>=0&&properties[i].id<=31;
                                coordinates[i].x=Math.max(0,Math.min(width-1,coordinates[i].x));
                                coordinates[i].y=Math.max(0,Math.min(height-1,coordinates[i].y));
                                coordinates[i].pressure=1;coordinates[i].size=1;
                            }
                            if(valid) {
                                MotionEvent bounded=MotionEvent.obtain(event.getDownTime(),event.getEventTime(),event.getAction(),count,properties,coordinates,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
                                try {input(bounded);}finally {bounded.recycle();}
                            }
                        }
                    }}finally {event.recycle();}
                    break;
                }
                case BACK:
                    if(display!=null&&surface!=null) {
                        long now=SystemClock.uptimeMillis();
                        KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0);
                        KeyEvent up=new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0);
                        input(down);input(up);
                    }
                    break;
                case STOP:
                    closeDisplay();new Handler(Looper.getMainLooper()).postDelayed(()->System.exit(0),200);break;
                case REOPEN:
                    if(display!=null&&surface!=null)launchNavigator();break;
                default:throw new IllegalArgumentException("Unsupported navigation operation");
            }
            if(reply!=null){reply.writeNoException();reply.writeInt(1);reply.writeInt(display==null?-1:display.getDisplay().getDisplayId());reply.writeInt(surface!=null?1:0);reply.writeInt(inputs);}
            return true;
        } catch(Throwable error) {
            System.err.println("NAV_HOST operation="+code+" error="+error.getClass().getSimpleName()+": "+error.getMessage());
            if(reply!=null)reply.writeException(new IllegalStateException(error.getClass().getSimpleName()+": "+error.getMessage()));
            return true;
        } finally {Binder.restoreCallingIdentity(identity);}
    }
    private void attach(String pkg,Surface next,int w,int h,int dpi) throws Exception {
        boolean launch=display==null||!pkg.equals(provider);
        if(launch)closeDisplay();
        if(display==null) {
            // Destroy only this helper's task container when the helper is stopped.
            int flags=DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|256;
            display=context.getSystemService(DisplayManager.class).createVirtualDisplay("Jeland navigation",w,h,dpi,next,flags);
            if(display==null){next.release();throw new IllegalStateException("Virtual display unavailable");}
            if(display.getDisplay().getDisplayId()<=0){closeDisplay();next.release();throw new SecurityException("Own secondary display required");}
        } else {display.resize(w,h,dpi);display.setSurface(next);}
        if(surface!=null)surface.release();surface=next;width=w;height=h;provider=pkg;
        if(launch)launchNavigator();
    }
    private void launchNavigator() throws Exception {
            String pkg=provider;
            if(pkg==null||display==null)throw new IllegalStateException("No selected navigator");
            Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null){closeDisplay();throw new IllegalStateException("Selected navigator not installed");}
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle options=ActivityOptions.makeBasic().setLaunchDisplayId(display.getDisplay().getDisplayId()).toBundle();
            try {
                Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
                boolean started=false;
                for(Method method:Class.forName("android.app.IActivityTaskManager").getMethods())if(method.getName().equals("startActivityAsUser")) {
                    Object result;
                    if(method.getParameterTypes().length==12)result=method.invoke(service,null,"com.android.shell",null,intent,null,null,null,0,0,null,options,0);
                    else if(method.getParameterTypes().length==11)result=method.invoke(service,null,"com.android.shell",intent,null,null,null,0,0,null,options,0);
                    else continue;
                    if(((Integer)result)<0)throw new IllegalStateException("Navigator launch rejected: "+result);
                    started=true;break;
                }
                if(!started)throw new IllegalStateException("Unsupported activity launch API");
            }catch(Exception error){closeDisplay();throw error;}
    }
    private void input(InputEvent event) throws Exception {
        setDisplay.invoke(event,display.getDisplay().getDisplayId());
        if(!Boolean.TRUE.equals(inject.invoke(inputManager,event,0)))throw new IllegalStateException("Input rejected");
        inputs++;
    }
    private synchronized void closeDisplay() {
        if(display!=null){display.release();display=null;}
        if(surface!=null){surface.release();surface=null;}
        provider=null;
    }
}
