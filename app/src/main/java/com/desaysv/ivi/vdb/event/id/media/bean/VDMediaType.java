package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/* JADX INFO: loaded from: classes.dex */
public class VDMediaType implements Parcelable {
    public static final Parcelable.Creator<VDMediaType> CREATOR = new Parcelable.Creator<VDMediaType>() { // from class: com.desaysv.ivi.vdb.event.id.media.bean.VDMediaType.1
        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaType createFromParcel(Parcel parcel) {
            return new VDMediaType(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaType[] newArray(int i) {
            return new VDMediaType[i];
        }
    };
    private int mMediaType;
    private int mVdsLastSource;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDMediaType() {
    }

    protected VDMediaType(Parcel parcel) {
        this.mMediaType = parcel.readInt();
        this.mVdsLastSource = parcel.readInt();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.mMediaType);
        parcel.writeInt(this.mVdsLastSource);
    }

    public int getVdsLastSource() {
        return this.mVdsLastSource;
    }

    public void putVdsLastSource(int i) {
        this.mVdsLastSource = i;
    }

    public int getMediaType() {
        return this.mMediaType;
    }

    public void putMediaType(int i) {
        this.mMediaType = i;
    }

    public String toString() {
        return "VDMediaType{mediaType=" + this.mMediaType + "mVdsLastSource=" + this.mVdsLastSource + "}";
    }

    public static VDMediaType getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaType.class.getClassLoader());
        return (VDMediaType) vDEvent.getPayload().getParcelable("type");
    }

    public static Bundle createPayload(VDMediaType vDMediaType) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaType.class.getClassLoader());
        bundle.putParcelable("type", vDMediaType);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDMediaType vDMediaType) {
        return new VDEvent(i, createPayload(vDMediaType));
    }
}
