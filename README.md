# RecordScreen
[![](https://jitpack.io/v/handsomenuonuo/RecordScreen.svg)](https://jitpack.io/#handsomenuonuo/RecordScreen)  
  
一个录屏库，超级简单的使用方法！

## 导入方法
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```
```gradle
dependencies {
    implementation 'com.github.handsomenuonuo:RecordScreen:1.0.0'
}
```

## 使用方法
### 1、快速使用
* 在自定义Application的 `onCreate` 方法里面添加初始化 `initRecordScreen()` 方法
>#### kotlin
> ```kotlin
> override fun onCreate() {
>    super.onCreate()
>    initRecordScreen()
>}
>```
>#### java
> ```java
>@Override
>public void onCreate() {
>    super.onCreate();
>    RecordScreenInitKt.initRecordScreen(this);
>}
>```
* 在需要控制录屏的 `AppCompactActivity` 上加上注解 `@RecordScreen` 
>```kotlin
>@RecordScreen
>class MainActivity : AppCompatActivity() {
>...
>```
* 在 `AppCompactActivity` 里面添加无参无返回的方法，分别添加 `RecordScreenStart` 和 `RecordScreenStop` 注解，用于接收开始和结束的回调
>#### kotlin
>```kotlin
>@RecordScreenStart
>fun onRecordScreenStart() {
>}
>
>@RecordScreenStop
>fun onRecordScreenStop() {
>}
>```
>#### java
>```java
>@RecordScreenStart
>public void onStartRecord(){
>}
>
>@RecordScreenStop
>public void onStopRecord(){
>}
>```

* 开始录制
>#### kotlin
>```kotlin
>startRecordScreen()
>```
>#### java
>```java
>RecordScreenInitKt.startRecordScreen(this);
>```

* 结束录制
>#### kotlin
>```kotlin
>stopRecordScreen()
>```
>#### java
>```java
>RecordScreenInitKt.stopRecordScreen(this);
>```

### 2、自定义配置
参考RecordScreenConfig,自行配置，在开始录制方法里面传入参数即可。

### 3、其他调用方式
* 除了采用注解自动注册的方式，还可以自己调用注册Acticity的方法。
* 多种开始录制调用方式。

以上方式请自行查看。












