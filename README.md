# Translate-JSON-OWL
将JSON-RD格式的OWL文件中的中文属性翻译成英文。

## 翻译API

程序内置了三种翻译API

### 百度通用翻译API

需要先申请才能使用，申请完成后将`appid`和`securityKey`填入程序中：

```java
    private static final TransApi BAIDU_API = new TransApi("appid", "securityKey");
```

申请地址：https://api.fanyi.baidu.com/

### 谷歌翻译api(生成tk)

程序来源：https://github.com/junjun888/google-translater

### 谷歌翻译api(无需tk)

参考来源：https://www.greenhtml.com/archives/java-call-google-translate-api-for-free.html