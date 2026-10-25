package com.desaysv.ivi.vdb.event.id.media.bean;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import com.desaysv.ivi.vdb.event.VDEvent;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class VDMediaInfo implements Parcelable {
    public static final Parcelable.Creator<VDMediaInfo> CREATOR = new Parcelable.Creator<VDMediaInfo>() { // from class: com.desaysv.ivi.vdb.event.id.media.bean.VDMediaInfo.1
        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaInfo createFromParcel(Parcel parcel) {
            return new VDMediaInfo(parcel);
        }

        @Override // android.os.Parcelable.Creator
        /* JADX INFO: renamed from: a, reason: merged with bridge method [inline-methods] */
        public VDMediaInfo[] newArray(int i) {
            return new VDMediaInfo[i];
        }
    };
    private String album;
    private byte[] albumBuffer;
    private String albumImgUrl;
    private String artist;
    private int collectStatus;
    private String composer;
    private HashMap extras = new HashMap();
    private String fileName;
    private String fileUrl;
    private String genre;
    private int id;
    private int infoType;
    private String lyrics;
    private int mediaType;
    private String programType;
    private String title;

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    public VDMediaInfo() {
    }

    public int getId() {
        return this.id;
    }

    public void putId(int i) {
        this.id = i;
    }

    public int getMediaType() {
        return this.mediaType;
    }

    public void putMediaType(int i) {
        this.mediaType = i;
    }

    public String getTitle() {
        return this.title;
    }

    public void putTitle(String str) {
        this.title = str;
    }

    public String getArtist() {
        return this.artist;
    }

    public void putArtist(String str) {
        this.artist = str;
    }

    public String getAlbum() {
        return this.album;
    }

    public void putAlbum(String str) {
        this.album = str;
    }

    public String getComposer() {
        return this.composer;
    }

    public void putComposer(String str) {
        this.composer = str;
    }

    public String getGenre() {
        return this.genre;
    }

    public void putGenre(String str) {
        this.genre = str;
    }

    public String getAlbumImgUrl() {
        return this.albumImgUrl;
    }

    public void putAlbumImgUrl(String str) {
        this.albumImgUrl = str;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void putFileName(String str) {
        this.fileName = str;
    }

    public String getFileUrl() {
        return this.fileUrl;
    }

    public void putFileUrl(String str) {
        this.fileUrl = str;
    }

    public int getCollectStatus() {
        return this.collectStatus;
    }

    public byte[] getAlbumBuffer() {
        return this.albumBuffer;
    }

    public void setAlbumBuffer(byte[] bArr) {
        this.albumBuffer = bArr;
    }

    public void putCollectStatus(int i) {
        this.collectStatus = i;
    }

    public int getInfoType() {
        return this.infoType;
    }

    public void putInfoType(int i) {
        this.infoType = i;
    }

    public String getLyrics() {
        return this.lyrics;
    }

    public void putLyrics(String str) {
        this.lyrics = str;
    }

    public String getProgramType() {
        return this.programType;
    }

    public void putProgramType(String str) {
        this.programType = str;
    }

    protected VDMediaInfo(Parcel parcel) {
        readFromParcel(parcel);
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(this.id);
        parcel.writeInt(this.mediaType);
        parcel.writeInt(this.collectStatus);
        parcel.writeInt(this.infoType);
        parcel.writeString(this.title);
        parcel.writeString(this.artist);
        parcel.writeString(this.album);
        parcel.writeString(this.composer);
        parcel.writeString(this.genre);
        parcel.writeString(this.albumImgUrl);
        parcel.writeString(this.fileName);
        parcel.writeString(this.fileUrl);
        parcel.writeByteArray(this.albumBuffer);
        parcel.writeString(this.lyrics);
        parcel.writeString(this.programType);
        parcel.writeMap(this.extras);
    }

    private void readFromParcel(Parcel parcel) {
        this.id = parcel.readInt();
        this.mediaType = parcel.readInt();
        this.collectStatus = parcel.readInt();
        this.infoType = parcel.readInt();
        this.title = parcel.readString();
        this.artist = parcel.readString();
        this.album = parcel.readString();
        this.composer = parcel.readString();
        this.genre = parcel.readString();
        this.albumImgUrl = parcel.readString();
        this.fileName = parcel.readString();
        this.fileUrl = parcel.readString();
        this.albumBuffer = parcel.createByteArray();
        this.lyrics = parcel.readString();
        this.programType = parcel.readString();
        try {
            this.extras = parcel.readHashMap(getClass().getClassLoader());
        } catch (Exception e2) {
        }
    }

    public String toString() {
        return "VDMediaInfo{id=" + this.id + ", mediaType = " + this.mediaType + ", collectStatus = " + this.collectStatus + ", title = " + this.title + ", artist = " + this.artist + ", album = " + this.album + ", composer = " + this.composer + ", genre = " + this.genre + ", albumImgUrl = " + this.albumImgUrl + ", fileName = " + this.fileName + ", fileUrl = " + this.fileUrl + ", infoType = " + this.infoType + ", programType = " + this.programType + ", lyrics = " + this.lyrics + ", extras = " + this.extras.toString() + "}";
    }

    public static VDMediaInfo getValue(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getPayload() == null) {
            return null;
        }
        vDEvent.getPayload().setClassLoader(VDMediaInfo.class.getClassLoader());
        return (VDMediaInfo) vDEvent.getPayload().getParcelable("info");
    }

    public static Bundle createPayload(VDMediaInfo vDMediaInfo) {
        Bundle bundle = new Bundle();
        bundle.setClassLoader(VDMediaInfo.class.getClassLoader());
        bundle.putParcelable("info", vDMediaInfo);
        return bundle;
    }

    public static VDEvent createEvent(int i, VDMediaInfo vDMediaInfo) {
        return new VDEvent(i, createPayload(vDMediaInfo));
    }

    public Map<String, String> getExtras() {
        return this.extras;
    }

    public void setExtras(HashMap<String, String> map) {
        if (map == null) {
            map = new HashMap<>();
        }
        this.extras = map;
    }

    public void putExtra(String str, String str2) {
        this.extras.put(str, str2);
    }

    public Object getExtra(String str) {
        return this.extras.get(str);
    }
}
