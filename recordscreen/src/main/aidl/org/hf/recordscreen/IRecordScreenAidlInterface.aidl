// IRecordScreenAidlInterface.aidl
package org.hf.recordscreen;
import org.hf.recordscreen.RecordScreenListener;

// Declare any non-default types here with import statements

interface IRecordScreenAidlInterface {

      void setListener(RecordScreenListener listener);

      void stopRecord();
}