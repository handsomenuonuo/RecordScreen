# RecordScreen2.0
[![](https://jitpack.io/v/handsomenuonuo/RecordScreen.svg)](https://jitpack.io/#handsomenuonuo/RecordScreen)
  
一个录屏库，超级简单的使用方法！相比1.0版本，简化了使用方式！

## 导入方法
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```
```gradle
dependencies {
    implementation 'com.github.handsomenuonuo:RecordScreen:2.0.0'
}
```

## 使用方法，超简单，只需要调用开始和结束即可
>#### kotlin
> ```kotlin
>startRecordScreen(callback,recordScreenConfig)
>
>stopRecordScreen()
>```
>#### java
> ```java
> RecordScreenKt.startRecordScreen(this,callback,config);
>
>RecordScreenKt.stopRecordScreen();
>```

### 2、自定义配置
参考RecordScreenConfig,自行配置，在开始录制方法里面传入参数即可。












