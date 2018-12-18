package com.weaver.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

/**
 * 重打包apk使用的工具
 *
 * 部分格式不能压缩，否则会用assets.openRawResourceFd时会出错
 *
 * Created by zhoulei on 2017/11/24.
 */
public class ApkUtils {
  /* these formats are already compressed, or don't compress well */
  private static final Set<String> sNoCompressExt = new HashSet<>(Arrays.asList(new String[]{
      ".jpg", ".jpeg", ".png", ".gif",
      ".wav", ".mp2", ".mp3", ".ogg", ".aac",
      ".mpg", ".mpeg", ".mid", ".midi", ".smf", ".jet",
      ".rtttl", ".imy", ".xmf", ".mp4", ".m4a",
      ".m4v", ".3gp", ".3gpp", ".3g2", ".3gpp2",
      ".amr", ".awb", ".wma", ".wmv", ".webm", ".mkv",
      ".webp"
  }));

  public static boolean okToCompressed(String zipPath) {
    if ("resources.arsc".equals(zipPath)) {
      //不要压缩resources.arsc文件，这个文件会被频凡读取，压缩会影响性能
      return false;
    }
    String ext = getFileExt(zipPath);
    return !sNoCompressExt.contains(ext) && !zipPath.contains("/raw/");
  }

  public static void setNoCompress(ZipEntry zipEntry, File origin) throws IOException {
    zipEntry.setCompressedSize(origin.length());
    zipEntry.setMethod(ZipEntry.STORED);
    zipEntry.setCrc(getCrc32(origin));
  }

  public static void setNoCompress(ZipEntry zipEntry, byte[] data) throws IOException {
    zipEntry.setCompressedSize(data.length);
    zipEntry.setMethod(ZipEntry.STORED);
    zipEntry.setCrc(getCrc32(data));
  }

  private static String getFileExt(String fileName) {
    int lastDot = fileName.lastIndexOf(".");
    if (lastDot >= 0) {
      return fileName.substring(lastDot);
    } else {
      return "";
    }
  }

  private static long getCrc32(File file) throws IOException {
    BufferedInputStream bufferedInputStream = null;
    try {
      bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
      byte[] buffer = new byte[512];
      int length;
      CRC32 crc32 = new CRC32();
      crc32.reset();
      while ((length = bufferedInputStream.read(buffer)) > 0) {
        crc32.update(buffer, 0, length);
      }
      return crc32.getValue();
    } finally {
      IOUtils.closeQuietly(bufferedInputStream);
    }
  }

  private static long getCrc32(byte[] data) throws IOException {
    CRC32 crc32 = new CRC32();
    crc32.reset();
    crc32.update(data, 0, data.length);
    return crc32.getValue();
  }
}
