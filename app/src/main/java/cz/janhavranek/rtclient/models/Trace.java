package cz.janhavranek.rtclient.models;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Trace {

    private int timestamp;
    private short duration;
    private boolean readerToTag;
    private byte[] data;
    private byte[] parity;

    public Trace(byte[] trace, int traceLen) {
        ByteBuffer bb = ByteBuffer.wrap(trace, 0, traceLen);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        timestamp = bb.getInt();
        duration = bb.getShort();

        short dataLen = bb.getShort();
        if ((dataLen & 0x8000) == 0) readerToTag = true;
        dataLen &= 0x7fff;

        data = new byte[dataLen];
        bb.get(data, 0, dataLen);

        int parityLen = bb.remaining();
        parity = new byte[parityLen];
        bb.get(parity, 0, parityLen);

        if (bb.remaining() != 0) {
            Log.w("Trace constructor", "bytes left in trace buffer");
        }
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public short getDuration() {
        return duration;
    }

    public void setDuration(short duration) {
        this.duration = duration;
    }

    public boolean isReaderToTag() {
        return readerToTag;
    }

    public void setReaderToTag(boolean readerToTag) {
        this.readerToTag = readerToTag;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getParity() {
        return parity;
    }

    public void setParity(byte[] parity) {
        this.parity = parity;
    }

    public boolean hasParityError() {
        return !checkDataParity();
    }

    public String getDataAsHexString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (checkByteParity(i)) {
                sb.append(String.format("%02x ", data[i]));
            } else {
                sb.append(String.format("%02x! ", data[i]));
            }
        }
        return sb.toString();
    }

    private boolean checkDataParity() {
        for (int i = 0; i < data.length; i++) {
            if (!checkByteParity(i)) return false;
        }
        return true;
    }

    private boolean checkByteParity(int i) {
        int p = (parity[i >> 3] >> (7 - (i & 7))) & 1;
        byte b = data[i];
        int bitCnt = Integer.bitCount(b) + p;
        return bitCnt % 2 == 1;
    }

}
