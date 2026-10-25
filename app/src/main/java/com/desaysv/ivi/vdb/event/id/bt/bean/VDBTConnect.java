package com.desaysv.ivi.vdb.event.id.bt.bean;
import android.os.Parcel;
import android.os.Parcelable;

/** Read-only OEM wire ABI; MAC is not exposed in diagnostics. */
public final class VDBTConnect implements Parcelable {
    private final String mac;
    public final int status;
    private final int control;
    public VDBTConnect(int status){mac=null;this.status=status;control=1;}
    private VDBTConnect(Parcel p){mac=p.readString();status=p.readInt();control=p.readInt();}
    public void writeToParcel(Parcel p,int f){p.writeString(mac);p.writeInt(status);p.writeInt(control);}
    public int describeContents(){return 0;}
    public static final Creator<VDBTConnect> CREATOR=new Creator<VDBTConnect>(){
        public VDBTConnect createFromParcel(Parcel p){return new VDBTConnect(p);}
        public VDBTConnect[] newArray(int n){return new VDBTConnect[n];}
    };
}
