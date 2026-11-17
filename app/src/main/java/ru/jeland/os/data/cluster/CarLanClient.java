package ru.jeland.os.data.cluster;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.Parcel;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One CarLan client with its own 300 ms limiter and a shared single-thread scheduler. */
final class CarLanClient implements ServiceConnection {
    private static final ScheduledThreadPoolExecutor WORKER = createWorker();
    private static final String PACKAGE = "com.desaysv.ivi.vds.carlan";
    private final Context context;
    private final Consumer<String> report;
    private final QnxClusterWindow qnx;
    private volatile IBinder binder;
    private long nextSubmission;
    private boolean binding;
    private final java.util.List<Runnable> awaitingBinder = new java.util.ArrayList<>();

    CarLanClient(Context context, Consumer<String> report) {
        this(context,report,new QnxClusterWindow());
    }

    CarLanClient(Context context, Consumer<String> report, QnxClusterWindow qnx) {
        this.context = context;
        this.report = report;
        this.qnx = qnx;
        bind();
    }

    private static ScheduledThreadPoolExecutor createWorker() {
        ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "CarLanJaecoo");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
        worker.prestartAllCoreThreads();
        return worker;
    }

    private void bind() {
        if (binder != null || binding) return;
        Intent intent = new Intent("action.desaysv.ivi.vds.carlan.SERVICE").setPackage(PACKAGE);
        try { binding = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            report.accept("CARLAN_BIND requested=" + binding);
        } catch (Exception error) { binding=false; report.accept("CARLAN_BIND_ERROR " + error); }
    }

    private synchronized void execute(Runnable action) {
        if (WORKER.getQueue().size() < 30) {
            long now = android.os.SystemClock.uptimeMillis();
            long delay = Math.max(0, nextSubmission - now);
            WORKER.schedule(action, delay, TimeUnit.MILLISECONDS);
            nextSubmission = now + delay + 300;
        }
    }

    /** A sleep cycle may leave both the FTP socket and the service binding stale. */
    void resumeAfterSleep() {
        execute(qnx::close);
        if (binder == null || !binder.isBinderAlive()) {
            try { context.unbindService(this); } catch (IllegalArgumentException ignored) {}
            binder = null; binding = false;
            bind();
        }
        report.accept("CARLAN_WAKE_RESET");
    }

    void setWindowVisible(boolean visible) { setWindowVisible(visible,success -> {}); }

    void setWindowVisible(boolean visible, Consumer<Boolean> completion) {
        execute(() -> {
            boolean success=false;
            try { qnx.setVisible(visible, report); success=true; }
            catch (Exception error) { qnx.close(); report.accept("QNX_VISIBILITY_ERROR visible=" + visible + " " + error); }
            completion.accept(success);
        });
    }

    /** Release navigation ownership instead of announcing another foreground navigation frame. */
    void releaseCluster(Consumer<Boolean> completion) {
        VDNaviDigitalInfo stopped = new VDNaviDigitalInfo();
        stopped.setCameraType(3);stopped.setNaviStatus(4);stopped.setSpeedingInfo(0);stopped.setWarningMessage(-1);
        sendResult(VDNaviDigitalInfo.createEvent(721701,stopped),"CARLAN_NAVIGATION_STOP status=4",ok -> {
            if(!ok){completion.accept(false);return;}
            VDNaviDisplayArea area = new VDNaviDisplayArea();
            area.setNaviDisplayArea(9);area.setNaviDisplayAreaResult("true");
            sendResult(VDNaviDisplayArea.createEvent(721702,area),"CARLAN_AREA area=9",selected -> {
                if(!selected){completion.accept(false);return;}
                VDNaviDisplayCluster display = new VDNaviDisplayCluster();
                display.setNaviFrontDeskStatus("false");display.setDisplayCluster("false");display.setPerspective(0);
                display.setPerspectiveResult("false");display.setRequestDisplayNaviArea("false");
                sendResult(VDNaviDisplayCluster.createEvent(721699,display),"CARLAN_RELEASE front=false display=false request=false",completion);
            });
        });
    }

    void displayAreaOpen(int area) {
        displayAreaOpen(area, null);
    }

    void displayAreaOpen(int area, Runnable afterDisplay) {
        VDNaviDisplayArea selected = new VDNaviDisplayArea();
        selected.setNaviDisplayArea(area);
        selected.setNaviDisplayAreaResult("true");
        send(VDNaviDisplayArea.createEvent(721702, selected), "CARLAN_AREA area=" + area);
        VDNaviDisplayCluster display = new VDNaviDisplayCluster();
        display.setNaviFrontDeskStatus("true");
        display.setDisplayCluster("true");
        display.setPerspective(0);
        display.setPerspectiveResult("false");
        display.setRequestDisplayNaviArea("false");
        send(VDNaviDisplayCluster.createEvent(721699, display),
            "CARLAN_DISPLAY front=true display=true request=false", afterDisplay);
    }

    private void send(VDEvent event, String description) {
        send(event, description, null);
    }

    private void send(VDEvent event, String description, Runnable afterSend) {
        send(event,description,afterSend,null);
    }
    private void sendResult(VDEvent event, String description, Consumer<Boolean> completion) {
        send(event,description,null,completion);
    }
    private void send(VDEvent event, String description, Runnable afterSend, Consumer<Boolean> completion) {
        if (binder == null) {
            awaitingBinder.add(() -> send(event, description, afterSend,completion));
            bind();
            report.accept("CARLAN_WAIT_FOR_BINDER " + description);
            return;
        }
        execute(() -> {
            event.createTimeMillis();
            Parcel request = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                request.writeInterfaceToken("com.desaysv.ivi.vdb.IVDBus");
                request.writeInt(1);
                event.writeToParcel(request, 0);
                binder.transact(2, request, reply, 0);
                reply.readException();
                report.accept(description);
                if (afterSend != null) afterSend.run();
                if (completion != null) completion.accept(true);
            } catch (Throwable error) {
                report.accept("CARLAN_SEND_ERROR " + description + " " + error);
                if (completion != null) completion.accept(false);
            } finally {
                request.recycle();
                reply.recycle();
            }
        });
    }

    public void onServiceConnected(ComponentName name, IBinder value) {
        binder = value;
        binding = false;
        java.util.List<Runnable> pending = new java.util.ArrayList<>(awaitingBinder);
        awaitingBinder.clear();
        for (Runnable action : pending) action.run();
        report.accept("CARLAN_CONNECTED " + name);
    }
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
        binding = false;
        report.accept("CARLAN_DISCONNECTED " + name);
    }
    public void onBindingDied(ComponentName name) {
        binder = null;
        binding = false;
        report.accept("CARLAN_BINDING_DIED " + name);
    }
    public void onNullBinding(ComponentName name) {
        binder = null;
        binding = false;
        report.accept("CARLAN_NULL_BINDING " + name);
    }
}
