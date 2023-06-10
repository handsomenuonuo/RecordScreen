package org.hf.recordscreen;

import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.hf.recordscreen.RecordScreenInitKt;
import org.hf.recordscreen.annotation.RecordScreen;
import org.hf.recordscreen.annotation.RecordScreenStart;
import org.hf.recordscreen.annotation.RecordScreenStop;
import org.hf.recordscreen.bean.RecordScreenCallback;
import org.hf.recordscreen.bean.RecordScreenConfig;

/**********************************
 * @Name: MainActivity1
 * @Copyright： Antoco
 * @CreateDate： 2023/6/9 19:12
 * @author: huang feng
 * @Version： 1.0
 * @Describe:
 **********************************/
@RecordScreen
public class MainActivity1 extends AppCompatActivity {

    RecordScreenCallback callback = new RecordScreenCallback(){

        @Override
        public void onStartRecord() throws RemoteException {
            Log.e("test","onStartRecord  "+(Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()?"主线程":"子线程"));
        }

        @Override
        public void onStopRecord() throws RemoteException {
            Log.e("test","onStopRecord  "+(Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()?"主线程":"子线程"));
        }
    };

    RecordScreenConfig config = new RecordScreenConfig();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


//
        findViewById(R.id.start).setOnClickListener(v -> {
//            RecordScreenInitKt.getRecordScreenContext(this).startRecordScreen(callback);
            RecordScreenInitKt.startRecordScreen(this,callback,null);
            RecordScreenInitKt.startRecordScreen(this);
            RecordScreenInitKt.startRecordScreen(this);
            RecordScreenInitKt.startRecordScreen(this,config);
        });
        findViewById(R.id.stop).setOnClickListener(v -> {
            RecordScreenInitKt.stopRecordScreen(this);
        });

    }

    @RecordScreenStart
    public void onStartRecord(){
        Log.e("test","onStartRecord  "+(Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()?"主线程":"子线程"));
    }

    @RecordScreenStop
    public void onStopRecord(){
        Log.e("test","onStopRecord  "+(Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()?"主线程":"子线程"));
    }
}
