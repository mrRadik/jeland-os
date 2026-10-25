package com.desaysv.ivi.vdb.event.id.media.bean;
import android.os.*;
/** OEM wire layout. Tuner payloads are deliberately unsupported. */
public final class VDMediaItem implements Parcelable {
    public int mediaType;
    public VDMediaInfo mediaInfo;
    public VDMediaItem(int type) { mediaType=type; }
    private VDMediaItem(Parcel p) {
        mediaType=p.readInt(); mediaInfo=p.readParcelable(VDMediaInfo.class.getClassLoader());
        Parcelable radio=p.readParcelable(getClass().getClassLoader());
        Parcelable dab=p.readParcelable(getClass().getClassLoader());
        if(radio!=null||dab!=null)throw new BadParcelableException("Unexpected tuner payload");
    }
    public void writeToParcel(Parcel p,int flags) {p.writeInt(mediaType);p.writeParcelable(mediaInfo,flags);p.writeParcelable(null,flags);p.writeParcelable(null,flags);}
    public int describeContents(){return 0;}
    public static final Creator<VDMediaItem> CREATOR=new Creator<VDMediaItem>() {
        public VDMediaItem createFromParcel(Parcel p){return new VDMediaItem(p);}
        public VDMediaItem[] newArray(int n){return new VDMediaItem[n];}
    };
}
