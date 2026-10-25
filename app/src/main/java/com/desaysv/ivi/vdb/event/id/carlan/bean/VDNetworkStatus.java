package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Parcel;
import android.os.Parcelable;

/** Exact OEM parcel order. Addresses and commands are consumed but not retained. Read-only DTO. */
public final class VDNetworkStatus implements Parcelable {
    public final boolean connected,registered;
    public final String type;
    public final int level;
    private VDNetworkStatus(Parcel p) {
        connected=p.readByte()!=0;registered=p.readByte()!=0;type=p.readString();p.readByte();level=p.readInt();
        p.readString();p.readString();p.readString();p.readInt();p.readInt();p.readInt();
    }
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel p,int flags){throw new UnsupportedOperationException("Read-only network state");}
    public static final Creator<VDNetworkStatus> CREATOR=new Creator<VDNetworkStatus>() {
        public VDNetworkStatus createFromParcel(Parcel p){return new VDNetworkStatus(p);}
        public VDNetworkStatus[] newArray(int n){return new VDNetworkStatus[n];}
    };
}
