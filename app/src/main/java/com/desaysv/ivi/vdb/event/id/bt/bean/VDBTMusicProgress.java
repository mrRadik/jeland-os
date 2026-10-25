package com.desaysv.ivi.vdb.event.id.bt.bean;
import android.os.Parcel;
import android.os.Parcelable;

public final class VDBTMusicProgress implements Parcelable {
    public final long position,duration;
    public VDBTMusicProgress(long position,long duration){this.position=position;this.duration=duration;}
    private VDBTMusicProgress(Parcel p){position=p.readLong();duration=p.readLong();}
    public void writeToParcel(Parcel p,int f){p.writeLong(position);p.writeLong(duration);}
    public int describeContents(){return 0;}
    public static final Creator<VDBTMusicProgress> CREATOR=new Creator<VDBTMusicProgress>(){
        public VDBTMusicProgress createFromParcel(Parcel p){return new VDBTMusicProgress(p);}
        public VDBTMusicProgress[] newArray(int n){return new VDBTMusicProgress[n];}
    };
}
