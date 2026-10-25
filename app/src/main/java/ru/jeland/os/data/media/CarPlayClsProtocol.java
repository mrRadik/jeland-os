package ru.jeland.os.data.media;

import android.os.IBinder;
import android.os.Parcel;

/** OEM PhoneLinkData wire from l1.c / q1.u0; fixed CarPlay server=1, MEDIA_CONTROL=6 only. */
public final class CarPlayClsProtocol {
    public static final String SERVICE="com.desaysv.carplay.CarplayService";
    public static final String DESCRIPTOR="com.desaysv.ipc.IPhoneLinkManager";
    public interface Permit {boolean allowed();}
    public static String action(int action){switch(action){case 1:return "MEDIA_PLAY";case 2:return "MEDIA_PAUSE";
        case 4:return "MEDIA_PREVIOUS";case 5:return "MEDIA_NEXT";default:throw new IllegalArgumentException("Only four media actions allowed");}}
    static void write(Parcel p,String command){
        p.writeInt(1); // non-null typed PhoneLinkData
        p.writeInt(1);p.writeInt(6);p.writeString(command);p.writeInt(-1);
        p.writeByte((byte)0);p.writeByte((byte)0); // isGetter=false, isForeground=false
        p.writeByte((byte)0);p.writeByte((byte)0); // no object, no list (no device/connection request)
        p.writeByte((byte)0);p.writeByte((byte)1); // async, default success field
    }
    private static void send(IBinder binder,String command)throws Exception {
        Parcel p=Parcel.obtain();try {
            p.writeInterfaceToken(DESCRIPTOR);write(p,command);
            if(!binder.transact(1,p,null,IBinder.FLAG_ONEWAY))throw new IllegalStateException("CLS transaction unsupported");
        }finally {p.recycle();}
    }
    public static void sendPair(IBinder binder,int action,Permit permit)throws Exception {
        String base=action(action);
        if(binder==null||!DESCRIPTOR.equals(binder.getInterfaceDescriptor()))throw new SecurityException("Wrong CLS Binder");
        if(!permit.allowed())throw new SecurityException("Expired write authorization");
        // A release is mandatory even if DOWN partly succeeds or the app pauses. No action retries.
        try {send(binder,base+"_DOWN");Thread.sleep(100);}
        finally {send(binder,base+"_UP");}
    }
    private CarPlayClsProtocol(){}
}
