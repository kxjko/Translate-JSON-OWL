import bt.TransApi;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import gt.GoogleApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

/**
 * @author ：zhaohaomin
 * @date ：Created in 2020/4/7 8:28
 */

public class Translate {
    private static final Logger logger = LoggerFactory.getLogger(Translate.class);
    //纯英文正则表达式
    private static final Pattern ENG_PATTERN = Pattern.compile("^[a-z_A-Z0-9-.!@#$%\\\\^&*)(+={}\\[\\]/\",'<>~·`?:;|\\s]+$");
    //获取百度翻译API，申请地址https://api.fanyi.baidu.com/api/trans/product/desktop?req=detail
    private static final TransApi BAIDU_API = new TransApi("appid", "securityKey");
    //谷歌翻译API，生成ttk
    private static final GoogleApi GOOGLE_API = new GoogleApi();
    //使用哪个API进行翻译：0.百度通用翻译api, 1.谷歌翻译api(生成tk), 2.谷歌翻译api(无需tk)
    private static final int USED_API = 0;

    public void translate(String sourceFile, String targetFile) {
        //读取文件
        String data = fileToString(sourceFile);
        if (data == null) {
            return;
        }
        //关闭json中有“@type”时自动转换
        JSON.DEFAULT_PARSER_FEATURE |= Feature.DisableSpecialKeyDetect.getMask();
        //遍历最外层list
        JSONArray dataArray = JSONArray.parseArray(data);
        for (int i = 0; i < dataArray.size(); i++) {
            JSONObject object = dataArray.getJSONObject(i);
            String id = object.get("@id").toString();
            //筛选resource
            if (!id.contains("http://www.openkg.cn/COVID-19/goods/resource/")) {
                continue;
            }
            logger.info("待翻译的resource:{}", object.get("@id"));
            //遍历每个字段
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                String key = entry.getKey();
                //筛选property
                if (!key.contains("http://www.openkg.cn/COVID-19/goods/property/")) {
                    continue;
                }
                logger.info("待翻译的property:{}", key);
                List<Map<String, String>> valueList = new ArrayList<>();
                JSONArray valueArray = JSON.parseArray(entry.getValue().toString());
                //遍历property中的值
                for (int j = 0; j < valueArray.size(); j++) {
                    JSONObject valueObject = valueArray.getJSONObject(j);
                    String value = valueObject.getString("@value");
                    //无value无需处理
                    if (value == null) {
                        continue;
                    }
                    //language不是中文时无需处理
                    if (valueObject.get("@language") != null && !"zh".equals(valueObject.get("@language"))) {
                        continue;
                    }
                    Matcher matcher = ENG_PATTERN.matcher(value);
                    if (matcher.matches()) {
                        //value为纯英文，language设为en
                        valueObject.put("@language", "en");
                    } else {
                        //value不为纯英文，language设为zh
                        valueObject.put("@language", "zh");
                        //将value内容翻译为英文
                        logger.info("要翻译的文本:{}", value);
                        String engValue;
                        engValue = translateZh2En(value);
                        logger.info("翻译的结果为:{}", engValue);
                        //等待1秒，百度api限制
                        try {
                            sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Map<String, String> valueMap = new HashMap<>(2);
                        valueMap.put("@language", "en");
                        valueMap.put("@value", engValue);
                        valueList.add(valueMap);
                    }
                }
                //将翻译后的内容添加到原来的property中
                valueArray.addAll(valueList);
                //修改resource中的property
                object.put(key, valueArray);
            }
        }
        //写入文件
        writeToFile(dataArray.toJSONString(), targetFile);
    }

    private String fileToString(String fileName) {
        String jsonStr;
        logger.info("开始读取文件:{}", fileName);
        try {
            File jsonFile = new File(fileName);
            FileReader fileReader = new FileReader(jsonFile);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8);
            int ch;
            StringBuilder sb = new StringBuilder();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            fileReader.close();
            reader.close();
            jsonStr = sb.toString();
            logger.info("文件读取成功！");
            return jsonStr;
        } catch (Exception e) {
            logger.info("文件读取失败!" + e);
            return null;
        }
    }

    private void writeToFile(String text, String fileName) {
        logger.info("开始写入文件:{}", fileName);
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
            out.write(text);
            out.close();
            logger.info("文件写入成功！");
        } catch (IOException e) {
            logger.error("文件写入失败！" + e);
        }
    }

    private String translateZh2En(String text) {
        String langFrom = "zh";
        String langTo = "en";
        String result;
        try {
            if (USED_API == 0) {
                result = baiduTranslate(langFrom, langTo, text);
            } else if (USED_API == 1) {
                result = GOOGLE_API.translate(text, langFrom, langTo);
            } else {
                result = googleTranslate(langFrom, langTo, text);
            }
        } catch (Exception e) {
            logger.error("翻译失败" + e);
            result = "翻译失败:" + text;
        }
        return result;
    }

    private String googleTranslate(String langFrom, String langTo, String text) throws Exception {

        String url = "https://translate.googleapis.com/translate_a/single?" +
                "client=gtx&" +
                "sl=" + langFrom +
                "&tl=" + langTo +
                "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        String string1 = JSON.parseArray(response.toString()).getJSONObject(0).toJSONString();
        String string2 = JSON.parseArray(string1).getJSONObject(0).toJSONString();
        return JSON.parseArray(string2).getJSONObject(0).toJSONString();
    }

    private String baiduTranslate(String langFrom, String langTo, String text) {
        String result = BAIDU_API.getTransResult(text, langFrom, langTo);
        String transResult = JSON.parseObject(result).get("trans_result").toString();
        String resultArray = JSON.parseArray(transResult).get(0).toString();
        return JSON.parseObject(resultArray).get("dst").toString();
    }
}
