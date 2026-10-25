package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class VDMediaPlayActionInfo implements Parcelable {
    public static final Parcelable.Creator<VDMediaPlayActionInfo> CREATOR = new Parcelable.Creator<VDMediaPlayActionInfo>() { // from class: com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlayActionInfo.1
        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaPlayActionInfo createFromParcel(Parcel parcel) {
            return new VDMediaPlayActionInfo(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaPlayActionInfo[] newArray(int i) {
            return new VDMediaPlayActionInfo[i];
        }
    };
    private int collectStatus;
    private int cyclicMode;
    private int mDisPlayView;
    private int mIntent;
    private int mReason;
    private int mSeekPosition = 0;
    private int mSeekStatus;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public int getSeekPosition() {
        return this.mSeekPosition;
    }

    public void putSeekPosition(int i) {
        this.mSeekPosition = i;
    }

    public int getSeekStatus() {
        return this.mSeekStatus;
    }

    public void putSeekStatus(int i) {
        this.mSeekStatus = i;
    }

    public int getDisPlayView() {
        return this.mDisPlayView;
    }

    public void putDisPlayView(int i) {
        this.mDisPlayView = i;
    }

    public int getReason() {
        return this.mReason;
    }

    public void putReason(int i) {
        this.mReason = i;
    }

    public int getIntent() {
        return this.mIntent;
    }

    public void putIntent(int i) {
        this.mIntent = i;
    }

    public int getCyclicMode() {
        return this.cyclicMode;
    }

    public void putCyclicMode(int i) {
        this.cyclicMode = i;
    }

    public int getCollectStatus() {
        return this.collectStatus;
    }

    public void putCollectStatus(int i) {
        this.collectStatus = i;
    }

    public VDMediaPlayActionInfo() {
    }

    protected VDMediaPlayActionInfo(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.mSeekPosition);
        parcel.writeInt(this.mSeekStatus);
        parcel.writeInt(this.mDisPlayView);
        parcel.writeInt(this.mReason);
        parcel.writeInt(this.mIntent);
        parcel.writeInt(this.cyclicMode);
        parcel.writeInt(this.collectStatus);
    }

    private void readFromParcel(Parcel parcel) {
        this.mSeekPosition = parcel.readInt();
        this.mSeekStatus = parcel.readInt();
        this.mDisPlayView = parcel.readInt();
        this.mReason = parcel.readInt();
        this.mIntent = parcel.readInt();
        this.cyclicMode = parcel.readInt();
        this.collectStatus = parcel.readInt();
    }

    public String toString() {
        return "VDMediaPlayActionInfo{mSeekPosition = " + this.mSeekPosition + ", mSeekStatus = " + this.mSeekStatus + ", mDisPlayView = " + this.mDisPlayView + ", mOpenReason = " + this.mReason + ", mIntent = " + this.mIntent + ", cyclicMode = " + this.cyclicMode + ", collectStatus = " + this.collectStatus + '}';
    }
}
