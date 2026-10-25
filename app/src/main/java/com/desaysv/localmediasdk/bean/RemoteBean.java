package com.desaysv.localmediasdk.bean;

import android.os.Parcel;
import android.os.Parcelable;

/* JADX INFO: loaded from: classes.dex */
public class RemoteBean implements Parcelable {
    public static final Parcelable.Creator<RemoteBean> CREATOR = new Parcelable.Creator<RemoteBean>() { // from class: com.desaysv.localmediasdk.bean.RemoteBean.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public RemoteBean createFromParcel(Parcel parcel) {
            return new RemoteBean(parcel);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public RemoteBean[] newArray(int i) {
            return new RemoteBean[i];
        }
    };
    private String album;
    private String artist;
    private boolean collectStatus;
    private String componentId;
    private int duration;
    private String ensembleLabel;
    private String frequency;
    private String path;
    private String serviceId;
    private String source;
    private String title;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public RemoteBean() {
    }

    protected RemoteBean(Parcel parcel) {
        radFromParcel(parcel);
    }

    public void radFromParcel(Parcel parcel) {
        this.source = parcel.readString();
        this.title = parcel.readString();
        this.album = parcel.readString();
        this.artist = parcel.readString();
        this.duration = parcel.readInt();
        this.collectStatus = parcel.readBoolean();
        this.frequency = parcel.readString();
        this.ensembleLabel = parcel.readString();
        this.serviceId = parcel.readString();
        this.componentId = parcel.readString();
        this.path = parcel.readString();
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.source);
        parcel.writeString(this.title);
        parcel.writeString(this.album);
        parcel.writeString(this.artist);
        parcel.writeInt(this.duration);
        parcel.writeBoolean(this.collectStatus);
        parcel.writeString(this.frequency);
        parcel.writeString(this.ensembleLabel);
        parcel.writeString(this.serviceId);
        parcel.writeString(this.componentId);
        parcel.writeString(this.path);
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String str) {
        this.source = str;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getAlbum() {
        return this.album;
    }

    public void setAlbum(String str) {
        this.album = str;
    }

    public String getArtist() {
        return this.artist;
    }

    public void setArtist(String str) {
        this.artist = str;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int i) {
        this.duration = i;
    }

    public boolean isCollectStatus() {
        return this.collectStatus;
    }

    public void setCollectStatus(boolean z) {
        this.collectStatus = z;
    }

    public String getFrequency() {
        return this.frequency;
    }

    public void setFrequency(String str) {
        this.frequency = str;
    }

    public String getEnsembleLabel() {
        return this.ensembleLabel;
    }

    public void setEnsembleLabel(String str) {
        this.ensembleLabel = str;
    }

    public String getServiceId() {
        return this.serviceId;
    }

    public void setServiceId(String str) {
        this.serviceId = str;
    }

    public String getComponentId() {
        return this.componentId;
    }

    public void setComponentId(String str) {
        this.componentId = str;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String str) {
        this.path = str;
    }
}
