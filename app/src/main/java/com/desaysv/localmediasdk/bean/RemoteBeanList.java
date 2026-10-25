package com.desaysv.localmediasdk.bean;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class RemoteBeanList implements Parcelable {
    public static final Parcelable.Creator<RemoteBeanList> CREATOR = new Parcelable.Creator<RemoteBeanList>() { // from class: com.desaysv.localmediasdk.bean.RemoteBeanList.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public RemoteBeanList createFromParcel(Parcel parcel) {
            return new RemoteBeanList(parcel);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public RemoteBeanList[] newArray(int i) {
            return new RemoteBeanList[i];
        }
    };
    private List<RemoteBean> remoteBeanList = new ArrayList();
    private String source;
    private String type;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public RemoteBeanList() {
    }

    protected RemoteBeanList(Parcel parcel) {
        readFromParcel(parcel);
    }

    public void readFromParcel(Parcel parcel) {
        this.source = parcel.readString();
        this.type = parcel.readString();
        this.remoteBeanList = parcel.createTypedArrayList(RemoteBean.CREATOR);
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.source);
        parcel.writeString(this.type);
        parcel.writeTypedList(this.remoteBeanList);
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String str) {
        this.source = str;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String str) {
        this.type = str;
    }

    public List<RemoteBean> getRemoteBeanList() {
        return this.remoteBeanList;
    }

    public void setRemoteBeanList(List<RemoteBean> list) {
        this.remoteBeanList = list;
    }
}
