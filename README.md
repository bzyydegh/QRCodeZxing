# QRCodeZxing
基于zxing框架实现二维码扫描和生成
# 添加权限
    <!-- Android 6.0 运行时权限分为：危险权限和常规权限 -->
    <!--危险权限-->
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <!--常规权限-->
    <uses-permission android:name="android.permission.FLASHLIGHT"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    
## 更新日志
1.解决Android 6.0以上不能保存二维码图片和扫描二维码
