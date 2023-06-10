package org.hf.recordscreen;

import android.app.Application;

import org.hf.recordscreen.RecordScreenInitKt;


/**********************************
 * @Name:         App
 * @Copyright：  Antoco
 * @CreateDate： 2023/6/9 18:54
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
public class App extends Application {


    @Override
    public void onCreate() {
        super.onCreate();
        RecordScreenInitKt.initRecordScreen(this);
    }
}
