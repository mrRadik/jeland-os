package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/* JADX INFO: loaded from: classes.dex */
public class VDMediaPlay implements Parcelable {
    public static final Parcelable.Creator<VDMediaPlay> CREATOR = new Parcelable.Creator<VDMediaPlay>() { // from class: com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlay.1
        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaPlay createFromParcel(Parcel parcel) {
            return new VDMediaPlay(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaPlay[] newArray(int i) {
            return new VDMediaPlay[i];
        }
    };
    private long actions;
    private VDMediaPlayActionInfo mediaPlayActionInfo;
    private int mediaType;
    private int playAction;
    private int playStatus;
    private int searchStatus;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDMediaPlay() {
    }

    public int getMediaType() {
        return this.mediaType;
    }

    public void putMediaType(int i) {
        this.mediaType = i;
    }

    public int getPlayStatus() {
        return this.playStatus;
    }

    public void putPlayStatus(int i) {
        this.playStatus = i;
    }

    public int getPlayAction() {
        return this.playAction;
    }

    public void putPlayAction(int i) {
        this.playAction = i;
    }

    public VDMediaPlayActionInfo getMediaPlayActionInfo() {
        return this.mediaPlayActionInfo;
    }

    public void putMediaPlayActionInfo(VDMediaPlayActionInfo vDMediaPlayActionInfo) {
        this.mediaPlayActionInfo = vDMediaPlayActionInfo;
    }

    public int getSearchStatus() {
        return this.searchStatus;
    }

    public void putSearchStatus(int i) {
        this.searchStatus = i;
    }

    public long getActions() {
        return this.actions;
    }

    public void setActions(long j) {
        this.actions = j;
    }

    protected VDMediaPlay(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.mediaType);
        parcel.writeInt(this.playStatus);
        parcel.writeInt(this.playAction);
        parcel.writeInt(this.searchStatus);
        parcel.writeLong(this.actions);
        parcel.writeParcelable(this.mediaPlayActionInfo, i);
    }

    private void readFromParcel(Parcel parcel) {
        this.mediaType = parcel.readInt();
        this.playStatus = parcel.readInt();
        this.playAction = parcel.readInt();
        this.searchStatus = parcel.readInt();
        this.actions = parcel.readLong();
        this.mediaPlayActionInfo = (VDMediaPlayActionInfo) parcel.readParcelable(VDMediaPlayActionInfo.class.getClassLoader());
    }

    public String toString() {
        return "VDMediaPlay{mediaType=" + this.mediaType + ", playStatus=" + this.playStatus + ", playAction=" + this.playAction + ", actions=" + this.actions + ", searchStatus=" + this.searchStatus + ", mMediaPlayActionInfo=" + this.mediaPlayActionInfo + "}";
    }

    public static VDMediaPlay getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaPlay.class.getClassLoader());
        return (VDMediaPlay) vDEvent.getPayload().getParcelable("status");
    }

    public static Bundle createPayload(VDMediaPlay vDMediaPlay) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaPlay.class.getClassLoader());
        bundle.putParcelable("status", vDMediaPlay);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDMediaPlay vDMediaPlay) {
        return new VDEvent(i, createPayload(vDMediaPlay));
    }
}
