package com.desaysv.ivi.vdb.event.id.bt.bean;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** All OEM wire fields consumed, artwork kept encoded: no bitmap allocation or URI loading. */
public final class VDBTMusicInfo implements Parcelable {
    public final String title,artist,album,mediaId;
    public final long position,duration;
    public final int state;
    private final String subtitle,description,iconUri,mediaUri,artUri,genre;
    private final Bundle extras;
    private final byte[] icon,art;
    private final long totalTracks,track;
    public VDBTMusicInfo(String title,String artist,String mediaId,int state){
        this.title=title;this.artist=artist;this.mediaId=mediaId;this.state=state;album=null;
        position=duration=totalTracks=track=0;subtitle=description=artUri=genre=null;iconUri=mediaUri="null";extras=null;icon=art=null;
    }
    private VDBTMusicInfo(Parcel p){
        title=p.readString();artist=p.readString();album=p.readString();position=p.readLong();duration=p.readLong();state=p.readInt();
        mediaId=p.readString();subtitle=p.readString();description=p.readString();extras=p.readBundle(getClass().getClassLoader());
        iconUri=p.readString();mediaUri=p.readString();icon=p.createByteArray();artUri=p.readString();genre=p.readString();
        totalTracks=p.readLong();track=p.readLong();art=p.createByteArray();
    }
    public void writeToParcel(Parcel p,int f){
        p.writeString(title);p.writeString(artist);p.writeString(album);p.writeLong(position);p.writeLong(duration);p.writeInt(state);
        p.writeString(mediaId);p.writeString(subtitle);p.writeString(description);p.writeBundle(extras);p.writeString(iconUri);p.writeString(mediaUri);
        p.writeByteArray(icon);p.writeString(artUri);p.writeString(genre);p.writeLong(totalTracks);p.writeLong(track);p.writeByteArray(art);
    }
    public byte[] getArtwork(){return art!=null&&art.length>0?art:icon;}
    public String getArtworkUri(){return artUri!=null?artUri:iconUri;}
    public int describeContents(){return 0;}
    public static final Creator<VDBTMusicInfo> CREATOR=new Creator<VDBTMusicInfo>(){
        public VDBTMusicInfo createFromParcel(Parcel p){return new VDBTMusicInfo(p);}
        public VDBTMusicInfo[] newArray(int n){return new VDBTMusicInfo[n];}
    };
}
