package com.weaver.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by zhoulei on 2017/9/21.
 */
public class LogUtils {
  private static final String TAG = "RES_REMAKER";
  private static Method androidLogM;

  static {
    try {
      Class androidLog = Class.forName("android.util.log");
      androidLogM = androidLog.getDeclaredMethod("d", String.class, String.class);
      androidLogM.setAccessible(true);
    } catch (Throwable t) {

    }
  }
  public static void println(String msg) {
    if (androidLogM != null) {
      try {
        androidLogM.invoke(TAG, msg);
      } catch (IllegalAccessException | InvocationTargetException e) {
        System.out.println(msg);
      }
    } else {
      System.out.println(msg);
    }
  }
}
