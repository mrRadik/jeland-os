package com.desaysv.ivi.vdb.event.id.vr.bean;
import android.os.Parcel;
import android.os.Parcelable;
/** Read-only OEM wire ABI: supplier, TTS type, status. */
public final class VDTTS implements Parcelable {
    private final String supplier,type; private final int status;
    public VDTTS(Parcel in){supplier=in.readString();type=in.readString();status=in.readInt();}
    public String getTtsType(){return type;} public int getTtsStatus(){return status;}
    public void writeToParcel(Parcel out,int flags){out.writeString(supplier);out.writeString(type);out.writeInt(status);}
    public int describeContents(){return 0;}
    public static final Creator<VDTTS> CREATOR=new Creator<VDTTS>(){
        public VDTTS createFromParcel(Parcel in){return new VDTTS(in);}
        public VDTTS[] newArray(int n){return new VDTTS[n];}
    };
}
