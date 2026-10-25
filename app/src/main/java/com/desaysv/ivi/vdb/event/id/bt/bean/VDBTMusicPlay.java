package com.desaysv.ivi.vdb.event.id.bt.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Wire-compatible subset of the OEM Parcelable. FQCN and field order are part of ABI. */
public final class VDBTMusicPlay implements Parcelable {
    public final int control, type;
    private final String query, mediaId, uri;
    private final Bundle extras;
    public VDBTMusicPlay(int control) {
        if(control!=1 && control!=2 && control!=4 && control!=5)throw new IllegalArgumentException("Only play/pause/previous/next");
        this.control=control;type=0;query=null;mediaId=null;uri="null";extras=null;
    }
    private VDBTMusicPlay(Parcel p) {
        control=p.readInt();type=p.readInt();query=p.readString();mediaId=p.readString();uri=p.readString();extras=p.readBundle(getClass().getClassLoader());
    }
    public void writeToParcel(Parcel p,int flags){p.writeInt(control);p.writeInt(type);p.writeString(query);p.writeString(mediaId);p.writeString(uri);p.writeBundle(extras);}
    public int describeContents(){return 0;}
    public static final Creator<VDBTMusicPlay> CREATOR=new Creator<VDBTMusicPlay>(){
        public VDBTMusicPlay createFromParcel(Parcel p){return new VDBTMusicPlay(p);}
        public VDBTMusicPlay[] newArray(int n){return new VDBTMusicPlay[n];}
    };
}
