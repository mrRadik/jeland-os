package com.desaysv.ivi.vdb.event.id.carlan.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;

/** Wire-compatible stock Desay DTO: int area, String result; Bundle key=data. */
public class VDNaviDisplayArea implements Parcelable {
    private int NaviDisplayArea;
    private String NaviDisplayAreaResult;
    public VDNaviDisplayArea(){}
    protected VDNaviDisplayArea(Parcel in){NaviDisplayArea=in.readInt();NaviDisplayAreaResult=in.readString();}
    public int getNaviDisplayArea(){return NaviDisplayArea;}
    public void setNaviDisplayArea(int value){NaviDisplayArea=value;}
    public String getNaviDisplayAreaResult(){return NaviDisplayAreaResult;}
    public void setNaviDisplayAreaResult(String value){NaviDisplayAreaResult=value;}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel out,int flags){out.writeInt(NaviDisplayArea);out.writeString(NaviDisplayAreaResult);}
    public static final Creator<VDNaviDisplayArea> CREATOR=new Creator<VDNaviDisplayArea>(){
        public VDNaviDisplayArea createFromParcel(Parcel in){return new VDNaviDisplayArea(in);}
        public VDNaviDisplayArea[] newArray(int size){return new VDNaviDisplayArea[size];}
    };
    public static VDNaviDisplayArea getValue(VDEvent event){
        if(event==null||event.getPayload()==null)return null;
        event.getPayload().setClassLoader(VDNaviDisplayArea.class.getClassLoader());
        return (VDNaviDisplayArea)event.getPayload().getParcelable("data");
    }
    public static VDEvent createEvent(int id,VDNaviDisplayArea value){
        Bundle payload=new Bundle();payload.setClassLoader(VDNaviDisplayArea.class.getClassLoader());payload.putParcelable("data",value);
        return new VDEvent(id,payload);
    }
}
