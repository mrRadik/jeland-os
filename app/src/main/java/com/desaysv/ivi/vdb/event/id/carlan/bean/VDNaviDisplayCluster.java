package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/* JADX INFO: loaded from: classes.dex */
public class VDNaviDisplayCluster implements Parcelable {
    public static final Parcelable.Creator<VDNaviDisplayCluster> CREATOR = new Parcelable.Creator<VDNaviDisplayCluster>() { // from class: com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster.1
        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviDisplayCluster createFromParcel(Parcel parcel) {
            return new VDNaviDisplayCluster(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDNaviDisplayCluster[] newArray(int i) {
            return new VDNaviDisplayCluster[i];
        }
    };
    private String DisplayCluster;
    private String NaviFrontDeskStatus;
    private int Perspective;
    private String PerspectiveResult;
    private String RequestDisplayNaviArea;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDNaviDisplayCluster() {
        this.Perspective = 0;
    }

    protected VDNaviDisplayCluster(Parcel parcel) {
        this.Perspective = 0;
        this.NaviFrontDeskStatus = parcel.readString();
        this.DisplayCluster = parcel.readString();
        this.Perspective = parcel.readInt();
        this.PerspectiveResult = parcel.readString();
        this.RequestDisplayNaviArea = parcel.readString();
    }

    public String getNaviFrontDeskStatus() {
        return this.NaviFrontDeskStatus;
    }

    public void setNaviFrontDeskStatus(String str) {
        this.NaviFrontDeskStatus = str;
    }

    public String getDisplayCluster() {
        return this.DisplayCluster;
    }

    public void setDisplayCluster(String str) {
        this.DisplayCluster = str;
    }

    public int getPerspective() {
        return this.Perspective;
    }

    public void setPerspective(int i) {
        this.Perspective = i;
    }

    public String getPerspectiveResult() {
        return this.PerspectiveResult;
    }

    public void setPerspectiveResult(String str) {
        this.PerspectiveResult = str;
    }

    public String getRequestDisplayNaviArea() {
        return this.RequestDisplayNaviArea;
    }

    public void setRequestDisplayNaviArea(String str) {
        this.RequestDisplayNaviArea = str;
    }

    public String toString() {
        return "VDNaviDisplayCluster{NaviFrontDeskStatus='" + this.NaviFrontDeskStatus + "', DisplayCluster='" + this.DisplayCluster + "', Perspective=" + this.Perspective + ", PerspectiveResult='" + this.PerspectiveResult + "', RequestDisplayNaviArea='" + this.RequestDisplayNaviArea + "'}";
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.NaviFrontDeskStatus);
        parcel.writeString(this.DisplayCluster);
        parcel.writeInt(this.Perspective);
        parcel.writeString(this.PerspectiveResult);
        parcel.writeString(this.RequestDisplayNaviArea);
    }

    public static VDNaviDisplayCluster getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDNaviDisplayCluster.class.getClassLoader());
        return (VDNaviDisplayCluster) vDEvent.getPayload().getParcelable("data");
    }

    public static Bundle createPayload(VDNaviDisplayCluster vDNaviDisplayCluster) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDNaviDisplayCluster.class.getClassLoader());
        bundle.putParcelable("data", vDNaviDisplayCluster);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDNaviDisplayCluster vDNaviDisplayCluster) {
        return new VDEvent(i, createPayload(vDNaviDisplayCluster));
    }
}
