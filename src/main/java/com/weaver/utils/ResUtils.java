package com.weaver.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pink.madis.apk.arsc.ResourceTableChunk;
import pink.madis.apk.arsc.StringPoolChunk;

/**
 * 一些res相关的工具方法
 *
 * Created by zhoulei on 2018/1/16.
 */
public class ResUtils {
  /**
   * 更改一个ResourceTableChunk对地址的引用
   *
   * @param tableChunk ResourceTable
   * @param resPathMap 地址映射表
   */
  public static void modifyResPath(ResourceTableChunk tableChunk, Map<String, String> resPathMap) {
    StringPoolChunk stringPoolChunk = tableChunk.getStringPool();
    List<String> strings = stringPoolChunk.strings;
    Map<Integer, String> modifiedStringMap = new HashMap<>();
    for(int i = 0; i < strings.size(); i++) {
      String s = strings.get(i);
      String mapped = resPathMap.get(s);
      if (mapped != null) {
        modifiedStringMap.put(i, mapped);
      }
    }
    for(Map.Entry<Integer, String> entry : modifiedStringMap.entrySet()) {
      strings.set(entry.getKey(), entry.getValue());
    }
  }
}
