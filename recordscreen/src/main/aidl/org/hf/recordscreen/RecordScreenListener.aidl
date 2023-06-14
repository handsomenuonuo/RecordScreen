// RecordScreenListener.aidl
package org.hf.recordscreen;

interface RecordScreenListener {
     void onStartRecord();
     void onRecordTime(int time);
     void onRecordFailure(String error);
     void onStopRecord();
}