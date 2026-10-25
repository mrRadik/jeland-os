package com.desaysv.ivi.vdb;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.desaysv.ivi.vdb.event.VDEvent;

public interface IVDBusCallback extends IInterface {
    void onVDBusCallback(VDEvent event) throws RemoteException;
    void onVDBusNotify(VDEvent event) throws RemoteException;

    abstract class Stub extends Binder implements IVDBusCallback {
        private static final String DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBusCallback";
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public IBinder asBinder() { return this; }
        public static IVDBusCallback asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IVDBusCallback) return (IVDBusCallback) local;
            return new Proxy(binder);
        }
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
            if (code == 1) {
                data.enforceInterface(DESCRIPTOR);
                onVDBusCallback(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null);
                reply.writeNoException();
                return true;
            }
            if (code == 2) {
                data.enforceInterface(DESCRIPTOR);
                onVDBusNotify(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements IVDBusCallback {
            private final IBinder remote;
            Proxy(IBinder binder) { remote = binder; }
            public IBinder asBinder() { return remote; }
            public void onVDBusCallback(VDEvent event) throws RemoteException { call(1, event, false); }
            public void onVDBusNotify(VDEvent event) throws RemoteException { call(2, event, true); }
            private void call(int code, VDEvent event, boolean oneway) throws RemoteException {
                Parcel data = Parcel.obtain(); Parcel reply = oneway ? null : Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (event != null) { data.writeInt(1); event.writeToParcel(data, 0); } else data.writeInt(0);
                    remote.transact(code, data, reply, oneway ? IBinder.FLAG_ONEWAY : 0);
                    if (!oneway) reply.readException();
                } finally { if (reply != null) reply.recycle(); data.recycle(); }
            }
        }
    }
}
