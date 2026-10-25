package com.desaysv.ivi.vdb.event.id.vr.bean;
import android.os.Parcel;
import android.os.Parcelable;
/** Read-only OEM wire ABI: the installed VDAsr writes exactly String key + int value. */
public final class VDAsr implements Parcelable {
    private final String key; private final int value;
    public VDAsr(Parcel in){key=in.readString();value=in.readInt();}
    public String getKey(){return key;} public int getValue(){return value;}
    public void writeToParcel(Parcel out,int flags){out.writeString(key);out.writeInt(value);}
    public int describeContents(){return 0;}
    public static final Creator<VDAsr> CREATOR=new Creator<VDAsr>(){
        public VDAsr createFromParcel(Parcel in){return new VDAsr(in);}
        public VDAsr[] newArray(int n){return new VDAsr[n];}
    };
}
