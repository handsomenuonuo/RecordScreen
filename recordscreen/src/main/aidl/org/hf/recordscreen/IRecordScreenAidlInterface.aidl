// IRecordScreenAidlInterface.aidl
package org.hf.recordscreen;
import org.hf.recordscreen.RecordScreenListener;
import org.hf.recordscreen.bean.RecordScreenConfig;
// Declare any non-default types here with import statements

interface IRecordScreenAidlInterface {

      void setListener(RecordScreenListener listener);

      void setRecordConfig(in RecordScreenConfig recordScreenConfig);

      void stopRecord();
}