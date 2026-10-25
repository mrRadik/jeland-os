package com.desaysv.ivi.vdb;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.desaysv.ivi.vdb.event.VDEvent;

public interface IVDBusNotify extends IInterface {
    void onVDBusNotify(VDEvent event) throws RemoteException;

    abstract class Stub extends Binder implements IVDBusNotify {
        private static final String DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBusNotify";
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public IBinder asBinder() { return this; }
        public static IVDBusNotify asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IVDBusNotify) return (IVDBusNotify) local;
            return new Proxy(binder);
        }
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
            if (code == 1) {
                data.enforceInterface(DESCRIPTOR);
                onVDBusNotify(data.readInt() != 0 ? VDEvent.CREATOR.createFromParcel(data) : null);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements IVDBusNotify {
            private final IBinder remote;
            Proxy(IBinder binder) { remote = binder; }
            public IBinder asBinder() { return remote; }
            public void onVDBusNotify(VDEvent event) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (event != null) { data.writeInt(1); event.writeToParcel(data, 0); } else data.writeInt(0);
                    remote.transact(1, data, null, IBinder.FLAG_ONEWAY);
                } finally { data.recycle(); }
            }
        }
    }
}
