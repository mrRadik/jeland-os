package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/* JADX INFO: loaded from: classes.dex */
public class VDMediaPlayTime implements Parcelable {
    public static final Parcelable.Creator<VDMediaPlayTime> CREATOR = new Parcelable.Creator<VDMediaPlayTime>() { // from class: com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlayTime.1
        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaPlayTime createFromParcel(Parcel parcel) {
            return new VDMediaPlayTime(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaPlayTime[] newArray(int i) {
            return new VDMediaPlayTime[i];
        }
    };
    private int duration;
    private int mediaType;
    private int position;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public int getPosition() {
        return this.position;
    }

    public void putPosition(int i) {
        this.position = i;
    }

    public int getDuration() {
        return this.duration;
    }

    public void putDuration(int i) {
        this.duration = i;
    }

    public int getMediaType() {
        return this.mediaType;
    }

    public void putMediaType(int i) {
        this.mediaType = i;
    }

    public VDMediaPlayTime() {
    }

    protected VDMediaPlayTime(Parcel parcel) {
        this.position = parcel.readInt();
        this.duration = parcel.readInt();
        this.mediaType = parcel.readInt();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.position);
        parcel.writeInt(this.duration);
        parcel.writeInt(this.mediaType);
    }

    public String toString() {
        return "VDMediaPlayTime{position=" + this.position + ", duration=" + this.duration + ", mediaType=" + this.mediaType + '}';
    }

    public static VDMediaPlayTime getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaPlayTime.class.getClassLoader());
        return (VDMediaPlayTime) vDEvent.getPayload().getParcelable("value");
    }

    public static Bundle createPayload(VDMediaPlayTime vDMediaPlayTime) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaPlayTime.class.getClassLoader());
        bundle.putParcelable("value", vDMediaPlayTime);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDMediaPlayTime vDMediaPlayTime) {
        return new VDEvent(i, createPayload(vDMediaPlayTime));
    }
}
