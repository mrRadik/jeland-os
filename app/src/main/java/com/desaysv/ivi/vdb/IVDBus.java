package com.desaysv.ivi.vdb;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.desaysv.ivi.vdb.event.VDEvent;

public interface IVDBus extends IInterface {
    VDEvent get(VDEvent event) throws RemoteException;
    void set(VDEvent event) throws RemoteException;
    void subscribe(int[] ids, int pid, String pkg, IVDBusCallback callback) throws RemoteException;
    void subscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) throws RemoteException;
    void unsubscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) throws RemoteException;

    abstract class Stub extends Binder implements IVDBus {
        private static final String DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBus";
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public IBinder asBinder() { return this; }
        public static IVDBus asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IVDBus) return (IVDBus) local;
            return new Proxy(binder);
        }
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
            data.enforceInterface(DESCRIPTOR);
            if (code == 1) {
                VDEvent result = get(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null);
                reply.writeNoException();
                if (result != null) { reply.writeInt(1); result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE); } else reply.writeInt(0);
                return true;
            }
            if (code == 2) { set(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null); reply.writeNoException(); return true; }
            if (code == 3) { subscribe(data.createIntArray(), data.readInt(), data.readString(), IVDBusCallback.Stub.asInterface(data.readStrongBinder())); reply.writeNoException(); return true; }
            if (code == 4) { subscribeCustomizedEvent(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null, IVDBusNotify.Stub.asInterface(data.readStrongBinder())); reply.writeNoException(); return true; }
            if (code == 5) { unsubscribeCustomizedEvent(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null, IVDBusNotify.Stub.asInterface(data.readStrongBinder())); reply.writeNoException(); return true; }
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements IVDBus {
            private final IBinder remote;
            Proxy(IBinder binder) { remote = binder; }
            public IBinder asBinder() { return remote; }
            private Parcel eventData(VDEvent event) {
                Parcel data = Parcel.obtain(); data.writeInterfaceToken(DESCRIPTOR);
                if (event != null) { data.writeInt(1); event.writeToParcel(data, 0); } else data.writeInt(0);
                return data;
            }
            public VDEvent get(VDEvent event) throws RemoteException {
                Parcel data = eventData(event); Parcel reply = Parcel.obtain();
                try { if (!remote.transact(1, data, reply, 0)) throw new RemoteException("IVDBus.get transaction rejected"); reply.readException(); return reply.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(reply) : null; }
                finally { reply.recycle(); data.recycle(); }
            }
            public void set(VDEvent event) throws RemoteException {
                Parcel data = eventData(event); Parcel reply = Parcel.obtain();
                try { if (!remote.transact(2, data, reply, 0)) throw new RemoteException("IVDBus.set transaction rejected"); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            public void subscribe(int[] ids, int pid, String pkg, IVDBusCallback callback) throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
                try { data.writeInterfaceToken(DESCRIPTOR); data.writeIntArray(ids); data.writeInt(pid); data.writeString(pkg); data.writeStrongBinder(callback != null ? callback.asBinder() : null); if (!remote.transact(3, data, reply, 0)) throw new RemoteException("IVDBus.subscribe transaction rejected"); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
            public void subscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) throws RemoteException { custom(4, event, notify); }
            public void unsubscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) throws RemoteException { custom(5, event, notify); }
            private void custom(int code, VDEvent event, IVDBusNotify notify) throws RemoteException {
                Parcel data = eventData(event); Parcel reply = Parcel.obtain();
                try { data.writeStrongBinder(notify != null ? notify.asBinder() : null); if (!remote.transact(code, data, reply, 0)) throw new RemoteException("IVDBus subscription transaction rejected"); reply.readException(); }
                finally { reply.recycle(); data.recycle(); }
            }
        }
    }
}
