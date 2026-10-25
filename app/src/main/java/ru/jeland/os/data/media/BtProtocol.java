package ru.jeland.os.data.media;
import android.os.Bundle;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.bt.bean.*;

/** OEM SVVDSMedia adapter and VDServiceDef, not guessed Android global media keys. */
public final class BtProtocol {
    public static final int A2DP=262167,AVRCP=262168,INFO=262945,PLAY=262948,PROGRESS=262952;
    public static final String PACKAGE="com.desaysv.ivi.vds.bt",SERVICE="com.desaysv.service.bluetooth.service.VDBTService",ACTION="action.desaysv.ivi.vds.bt.SERVICE";
    public static VDEvent command(int action){
        Bundle payload=new Bundle();payload.putParcelable("status",new VDBTMusicPlay(action));return new VDEvent(PLAY,payload);
    }
    public static VDEvent read(int event){
        if(event!=A2DP && event!=AVRCP && event!=INFO && event!=PROGRESS)throw new IllegalArgumentException("Unreviewed BT read");
        return new VDEvent(event,new Bundle());
    }
    public static <T> T value(VDEvent response,int event,Class<T> type){
        if(response==null)return null;
        if(response.getId()!=event)throw new IllegalArgumentException("BT response event mismatch");
        Bundle b=response.getPayload();if(b==null)return null;
        b.setClassLoader(VDBTMusicInfo.class.getClassLoader());
        if(b.getBoolean("IS_NULL",false))return null;
        Object v=b.get(event==A2DP || event==AVRCP?"status":"info");
        if(v==null)return null;if(!type.isInstance(v))throw new IllegalArgumentException("BT wire type mismatch: "+v.getClass().getName());return type.cast(v);
    }
    public static String state(int state){return state==3?"PLAYING":state==2?"PAUSED":state==1?"STOPPED":"raw "+state;}
    public static String result(int action,VDBTMusicInfo before,VDBTMusicInfo after){
        if(after==null)return "UNKNOWN: нет обратных данных";
        if(action==1 || action==2){
            int target=action==1?3:2;
            if(after.state!=target)return "NOT_CONFIRMED: state="+state(after.state);
            if(before==null)return "MATCH_WITHOUT_BASELINE";
            return before.state==target?"ALREADY_AT_TARGET":"STATE_CHANGED_TO_TARGET";
        }
        if(before==null)return "UNKNOWN: нет исходного трека";
        String oldKey=trackKey(before),newKey=trackKey(after);
        if(oldKey==null || newKey==null)return "UNKNOWN: нет идентификатора/названия трека";
        return oldKey.equals(newKey)?"TRACK_NOT_CHANGED":"TRACK_CHANGED: проверьте направление физически";
    }
    private static String trackKey(VDBTMusicInfo value){
        if(value.mediaId!=null && !value.mediaId.isEmpty())return value.mediaId+"|"+value.title+"|"+value.artist;
        return value.title==null || value.title.isEmpty()?null:value.title+"|"+value.artist;
    }
    private BtProtocol(){}
}
