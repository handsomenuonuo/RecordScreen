# RecordScreen2.0
[![](https://jitpack.io/v/handsomenuonuo/RecordScreen.svg)](https://jitpack.io/#handsomenuonuo/RecordScreen)
  
一个录屏库，超级简单的使用方法！相比1.0版本，简化了使用方式！

## 2.1更新了什么
* 修改部分Bug
* 新增内置悬浮框，通过[RecordScreenConfig.useFloatingView]()配置
* 新增录制时长回调
* 新增录制失败时的回调
* 新增录制时间控制，通过[RecordScreenConfig.totalRecordTime]()配置
* 新增录制分段控制，通过[RecordScreenConfig.perSectionTime]()配置
* 注意:___分段只针对设置确定的存储路径有效！___

## 导入方法
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```
```gradle
dependencies {
    implementation 'com.github.handsomenuonuo:RecordScreen:2.1.1'
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












