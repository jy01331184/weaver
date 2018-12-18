package com.weaver.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 解压缩
 * <p>
 * Created by zhoulei on 2017/9/21.
 */
public class ZipUtils {
  private static final String PATH_SEPARATOR = "/";

  /**
   * Copy entry
   *
   * @param original - zipEntry to copy
   * @return copy of the original entry
   */
  public static ZipEntry copy(ZipEntry original) {
    return copy(original, null);
  }

  /**
   * Copy entry with another name.
   *
   * @param original - zipEntry to copy
   * @param newName - new entry name, optional, if null, ogirinal's entry
   * @return copy of the original entry, but with the given name
   */
  public static ZipEntry copy(ZipEntry original, String newName) {
    ZipEntry copy = new ZipEntry(newName == null ? original.getName() : newName);
    if (original.getCrc() != -1) {
      copy.setCrc(original.getCrc());
    }
    if (original.getMethod() != -1) {
      copy.setMethod(original.getMethod());
    }
    if (original.getSize() >= 0) {
      copy.setSize(original.getSize());
    }
    if (original.getExtra() != null) {
      copy.setExtra(original.getExtra());
    }

    copy.setComment(original.getComment());
    copy.setTime(original.getTime());
    return copy;
  }

  public static void unpack(File zip, File target) throws IOException {
    ZipFile zf = null;
    try {
      zf = new ZipFile(zip);

      Enumeration<? extends ZipEntry> en = zf.entries();
      while (en.hasMoreElements()) {
        ZipEntry e = en.nextElement();

        InputStream is = zf.getInputStream(e);
        try {
          String name = e.getName();
          if (name != null) {
            File file = new File(target, name);
            if (e.isDirectory()) {
              FileUtils.forceMkdir(file);
            } else {
              FileUtils.forceMkdir(file.getParentFile());

              FileUtils.copy(is, file);
            }
          }
        } catch (IOException ze) {
          throw new IOException("Failed to unpack zip entry '" + e.getName());
        } finally {
          IOUtils.closeQuietly(is);
        }
      }
    } finally {
      if (zf != null) {
        try {
          zf.close();
        } catch (IOException e) {
          //ignore
        }
      }
    }
  }

  public static void pack(File sourceDir, File targetZip, int compressionLevel) throws IOException {
    if (!sourceDir.exists()) {
      throw new IOException("Given file '" + sourceDir + "' doesn't exist!");
    }
    ZipOutputStream out = null;
    try {
      out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetZip)));
      out.setLevel(compressionLevel);
      pack(sourceDir, "", out);
    } finally {
      IOUtils.closeQuietly(out);
    }
  }

  private static void pack(File dir, String pathPrefix, ZipOutputStream out) throws IOException {
    String[] filenames = dir.list();
    if (filenames == null) {
      if (!dir.exists()) {
        throw new IOException("Given file '" + dir + "' doesn't exist!");
      }
      throw new IOException("Given file is not a directory '" + dir + "'");
    }

    for (int i = 0; i < filenames.length; i++) {
      String filename = filenames[i];
      File file = new File(dir, filename);
      boolean isDir = file.isDirectory();
      String path = pathPrefix + file.getName(); // NOSONAR
      if (isDir) {
        path += PATH_SEPARATOR; // NOSONAR
      }

      // Create a ZIP entry
      String name = path;
      ZipEntry zipEntry = new ZipEntry(name);
      if (!file.isDirectory()) {
        zipEntry.setSize(file.length());
        if (!ApkUtils.okToCompressed(path)) {
          ApkUtils.setNoCompress(zipEntry, file);
        }
      }
      zipEntry.setTime(file.lastModified());

      out.putNextEntry(zipEntry);

      // Copy the file content
      if (!isDir) {
        FileUtils.copy(file, out);
      }

      out.closeEntry();

      // Traverse the directory
      if (isDir) {
        pack(file, path, out);
      }
    }
  }

//  public static void main(String[] args) {
//    File target = new File("/Users/zhoulei/Documents/develop/android/ResRemaker/demo/src/main/assets/demo-debug-30.apk");
//    File tmp = FileUtils.getTempFileFor(target);
//    try {
//      unpack(target, tmp);
//      target.delete();
//      pack(tmp, target, 9);
//      FileUtils.deleteDirectory(tmp);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//  }
}
