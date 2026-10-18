package com.desaysv.ivi.vdb.event;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

public class VDEvent implements Parcelable {
    public static final Parcelable.Creator<VDEvent> CREATOR = new Parcelable.Creator<VDEvent>() {
        public VDEvent createFromParcel(Parcel in) { return new VDEvent(in); }
        public VDEvent[] newArray(int size) { return new VDEvent[size]; }
    };

    private int mId;
    private Bundle mPayload;
    private int mThreadType;
    private long mTimeMillis;

    public VDEvent(int id) { mId = id; mThreadType = 0; }
    public VDEvent(int id, Bundle payload) { mId = id; mPayload = payload; mThreadType = 0; }
    protected VDEvent(Parcel in) {
        mId = in.readInt();
        mPayload = in.readBundle();
        mThreadType = in.readInt();
        mTimeMillis = in.readLong();
    }

    public int getId() { return mId; }
    public Bundle getPayload() { return mPayload; }
    public void setPayload(Bundle payload) { mPayload = payload; }
    public int getThreadType() { return mThreadType; }
    public void setThreadType(int type) { mThreadType = type; }
    public long getTimeMillis() { return mTimeMillis; }
    public void createTimeMillis() { mTimeMillis = SystemClock.elapsedRealtime(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
        out.writeBundle(mPayload);
        out.writeInt(mThreadType);
        out.writeLong(mTimeMillis);
    }
    public String toString() {
        return "VDEvent{id=" + mId + ", payload=" + mPayload + ", threadType=" + mThreadType + ", timeMillis=" + mTimeMillis + "}";
    }
}
