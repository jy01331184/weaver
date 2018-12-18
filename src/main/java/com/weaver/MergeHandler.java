package com.weaver;


import com.weaver.utils.ApkUtils;
import com.weaver.utils.Callback;
import com.weaver.utils.FileUtils;
import com.weaver.utils.IOUtils;
import com.weaver.utils.ManifestMerger;
import com.weaver.utils.ResIdUtils;
import com.weaver.utils.ResUtils;
import com.weaver.utils.VisitUtils;
import com.weaver.utils.ZipUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import guava.io.ByteStreams;
import pink.madis.apk.arsc.Chunk;
import pink.madis.apk.arsc.LibraryChunk;
import pink.madis.apk.arsc.PackageChunk;
import pink.madis.apk.arsc.PackageUtils;
import pink.madis.apk.arsc.ResourceFile;
import pink.madis.apk.arsc.ResourceTableChunk;
import pink.madis.apk.arsc.ResourceValue;
import pink.madis.apk.arsc.StringPoolChunk;
import pink.madis.apk.arsc.TypeChunk;
import pink.madis.apk.arsc.TypeSpecChunk;
import pink.madis.apk.arsc.XmlChunk;

import static com.weaver.utils.LogUtils.println;


/**
 * merge已编译资源
 * <p>
 * Created by zhoulei on 17/7/20.
 */
public class MergeHandler {
  private static final String MANIFEST_FILE = "AndroidManifest.xml";
  private static final String ARSC_FILE = "resources.arsc";
  private static final String RES_DIR = "res";
  private static final String ASSETS = "assets";
  private static final String PATH_SEPARATOR = "/";

  /**
   * 将编译后的资源目录合并。
   * 原则：
   * 1. arsc需要合并，mergeFrom的arsc中资源id视为public，merge的时候需要保留，mergeTo的arsc中资源id动态调整；
   * 2. manifest暂时不需要合并，用原有的AndroidManifest merge即可
   * 3. assets资源，其它xml资源, 以及普通图片资源等，如有冲突，直接报错
   * <p>
   * return 修改R.java的用的idMapping
   */
  public static MergeResult merge(File mergeTo, File mergeFrom) throws IOException {
    println("start merge");

    checkMergeFile(mergeTo, mergeFrom);

    final MergeResult mergeResult = new MergeResult();

    File mergeFromArsc = new File(mergeFrom, ARSC_FILE);
    File mergeToArsc = new File(mergeTo, ARSC_FILE);
    mergeArsc(mergeToArsc, mergeFromArsc, mergeResult, false);

    final Set<Integer> modifyTypes = new HashSet<>();
    modifyTypes.add(ResIdUtils.Modifier.TYPE_XML_RESOURCE_MAP);
    modifyTypes.add(ResIdUtils.Modifier.TYPE_XML_RESOURCES_VALUE_REF);
    //修改xml中的resId引用，arsc中的引用已经在mergeArsc的时候中更改了
    ResIdUtils.modifyRefResourceId(mergeTo, new ResIdUtils.Modifier() {
      @Override
      public int onModify(int originResId, int type) {
        if (modifyTypes.contains(type)) {
          if (mergeResult.idMapping == null) {
            return originResId;
          }
          Integer mapping = mergeResult.idMapping.get(originResId);
          if (mapping != null) {
            return mapping;
          }
        }
        return originResId;
      }
    }, modifyTypes);

    File mergeFromManifest = new File(mergeFrom, MANIFEST_FILE);
    File mergeToManifest = new File(mergeTo, MANIFEST_FILE);
    mergeManifest(mergeToManifest, mergeFromManifest);

    File mergeFromAssets = new File(mergeFrom, ASSETS);
    File mergeToAssets = new File(mergeTo, ASSETS);
    mergeDir(mergeToAssets, mergeFromAssets);

    File mergeFromRes = new File(mergeFrom, RES_DIR);
    File mergeToRes = new File(mergeTo, RES_DIR);
    mergeDir(mergeToRes, mergeFromRes);

    return mergeResult;
  }

  /**
   * 合并arsc、res、assets文件夹, 其它文件一律不采用
   * arsc合并不允许有资源id冲突，资源文件不允许有重名
   *
   * @param outputStream 合并输出流
   * @param pkgs         待合并的资源包
   */
  @Deprecated
  public static void combine(ZipOutputStream outputStream, ZipInputStream... pkgs) throws IOException {
    combine(outputStream, null, null, pkgs);
  }

  /**
   * 合并arsc、res、assets文件夹
   * arsc合并不允许有资源id冲突，资源文件不允许有重名
   *
   * @param outputStream 合并输出流
   * @param isRes        判断目录是否是resDir
   * @param pkgs         待合并的资源包
   */
  @Deprecated
  public static void combine(ZipOutputStream outputStream, final IsRes<ZipInputStream> isRes, final EntryMapper<ZipInputStream> entryMapper, ZipInputStream... pkgs) throws IOException {
    try {
      if (pkgs == null || pkgs.length == 0) {
        throw new IllegalArgumentException("combine pkg can't be null or empty");
      }
      if (outputStream == null) {
        throw new IllegalArgumentException("outputStream is null!");
      }
      combine(outputStream, new DefaultOption<ZipInputStream>() {
        @Override
        public IsRes<ZipInputStream> isResJudge() {
          return isRes;
        }

        @Override
        public EntryMapper<ZipInputStream> entryMapper() {
          return entryMapper;
        }
      }, pkgs);
    } finally {
      if (pkgs != null) {
        for (ZipInputStream pkg : pkgs) {
          IOUtils.closeQuietly(pkg);
        }
      }
      IOUtils.closeQuietly(outputStream);
    }
  }

  /**
   * 合并arsc、res、assets文件夹
   * arsc合并不允许有资源id冲突，资源文件不允许有重名
   *
   * @param outputStream 合并输出流
   * @param option       合并参数
   * @param pkgs         待合并的资源包
   */
  public static void combine(ZipOutputStream outputStream, Option<ZipInputStream> option, ZipInputStream... pkgs) throws IOException {
    outputStream.setLevel(9);

    Set<String> wroteEntries = new HashSet<>();

    ResourceFile[] arscs = new ResourceFile[pkgs.length];
    XmlChunk[] manifests = new XmlChunk[pkgs.length];
    Map<String, String>[] entryTransMaps = new HashMap[pkgs.length];

    byte[] buf = new byte[1024];
    ZipEntry currentEntry = null;
    final EntryMapper<ZipInputStream> entryMapper = option.entryMapper();
    for (int i = 0; i < pkgs.length; i++) {
      ZipInputStream pkg = pkgs[i];
      while ((currentEntry = pkg.getNextEntry()) != null) {
        String name = currentEntry.getName();
        if (!isCombineTarget(name, pkg, option.isResJudge(), option.mergeManifest(), option.mergeAssets())) {
          //资源文件之外的东西，只采用第一个包的
          continue;
        }
        if (ARSC_FILE.equals(name)) {
          //稍等再合并
          arscs[i] = (ResourceFile.fromInputStream(pkg));
          continue;
        }
        if (MANIFEST_FILE.equals(name)) {
          //稍后再合并
          byte[] data = ByteStreams.toByteArray(pkg);
          manifests[i] = (XmlChunk) Chunk.newInstance(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
          continue;
        }
        String newName = mapNameIfNeed(name, currentEntry.isDirectory(), entryMapper, pkg);
        if (!newName.equals(name)) {
          Map<String, String> transMap = entryTransMaps[i];
          if (transMap == null) {
            transMap = new HashMap<>();
            entryTransMaps[i] = transMap;
          }
          transMap.put(name, newName);

          name = newName;
        }
        if (wroteEntries.contains(name)) {
          if (currentEntry.isDirectory()) {
            //忽略文件夹的重复
            continue;
          }
          throw new IllegalStateException("duplicate entry:" + name + " found!");
        }

        ZipEntry outputEntry = ZipUtils.copy(currentEntry, name);

        outputStream.putNextEntry(outputEntry);
        int len;
        while ((len = pkg.read(buf)) > 0) {
          outputStream.write(buf, 0, len);
        }
        wroteEntries.add(name);
      }
    }

    List<ResourceFile> arscList = new ArrayList<>();
    for (int i = 0; i < arscs.length; i++) {
      ResourceFile arsc = arscs[i];
      if (arsc != null) {
        Map<String, String> transMap = entryTransMaps[i];
        if (transMap != null) {
          ResUtils.modifyResPath(VisitUtils.getResTableFrom(arsc), transMap);
        }
        arscList.add(arsc);
      }
    }

    ResourceFile mergedArsc = mergeArsc(arscList);
    if (mergedArsc != null) {
      ZipEntry zipEntry = new ZipEntry(ARSC_FILE);
      byte[] data = mergedArsc.toByteArray();
      ApkUtils.setNoCompress(zipEntry, data);
      outputStream.putNextEntry(zipEntry);
      outputStream.write(data);
    }

    List<XmlChunk> manifestList = new ArrayList<>();
    for (XmlChunk xmlChunk : manifests) {
      if (xmlChunk != null) {
        manifestList.add(xmlChunk);
      }
    }
    XmlChunk mergedManifest = mergeManifest(manifestList);

    if (mergedManifest != null) {
      ZipEntry zipEntry = new ZipEntry(MANIFEST_FILE);
      zipEntry.setMethod(ZipEntry.DEFLATED);
      byte[] data = mergedManifest.toByteArray();
      outputStream.putNextEntry(zipEntry);
      outputStream.write(data);
    }

    outputStream.flush();
  }

  private static <T> String mapNameIfNeed(String name, boolean isDirectory, EntryMapper<T> entryMapper, T pkg) {
    if (entryMapper != null) {
      String newName = entryMapper.map(name, isDirectory, pkg);
      if (newName == null) {
        throw new NullPointerException("entry name can't be null!");
      }
      return newName;
    }
    return name;
  }

  /**
   * 合并arsc、res、assets文件夹, 其它文件一律不采用
   *
   * @param outputStream   合并输出流
   * @param allowOverwrite 如果pkgs中存在同名资源，是否允许后面的pkg覆盖前面的pkg
   * @param pkgs           待合并的资源包
   */
  @Deprecated
  public static void combine(ZipOutputStream outputStream, boolean allowOverwrite, ZipFile... pkgs) throws IOException {
    combine(outputStream, null, allowOverwrite, pkgs);
  }

  /**
   * 合并arsc、res、assets文件夹, 其它文件一律不采用
   *
   * @param outputStream   合并输出流
   * @param isRes          判断是否是需要合并的资源
   * @param allowOverwrite 如果pkgs中存在同名资源，是否允许后面的pkg覆盖前面的pkg
   * @param pkgs           待合并的资源包
   */
  @Deprecated
  public static void combine(ZipOutputStream outputStream, IsRes<ZipFile> isRes, boolean allowOverwrite, ZipFile... pkgs) throws IOException {
    combine(outputStream, isRes, allowOverwrite, false, pkgs);
  }

  /**
   * 合并arsc、res、assets文件夹, Manifest优先采用第一个资源包的manifest
   *
   * @param outputStream   合并输出流
   * @param isRes          判断是否是需要合并的资源
   * @param allowOverwrite 如果pkgs中存在同名资源，是否允许后面的pkg覆盖前面的pkg
   * @param mergeManifest  是否需要对AndroidManifest.xml进行合并（二进制合并）
   * @param pkgs           待合并的资源包
   */
  @Deprecated
  public static void combine(ZipOutputStream outputStream, final IsRes<ZipFile> isRes, final boolean allowOverwrite, final boolean mergeManifest, ZipFile... pkgs) throws IOException {
    try {
      if (pkgs == null || pkgs.length == 0) {
        throw new IllegalArgumentException("combine pkg can't be null or empty");
      }
      if (outputStream == null) {
        throw new IllegalArgumentException("outputStream is null!");
      }

      combine(outputStream, new DefaultOption<ZipFile>() {
        @Override
        public IsRes<ZipFile> isResJudge() {
          return isRes;
        }

        @Override
        public boolean allowOverwrite() {
          return allowOverwrite;
        }

        @Override
        public boolean mergeManifest() {
          return mergeManifest;
        }
      }, pkgs);
    } finally {
      if (pkgs != null) {
        for (ZipFile pkg : pkgs) {
          IOUtils.closeQuietly(pkg);
        }
      }
      IOUtils.closeQuietly(outputStream);
    }
  }

  /**
   * 合并arsc、res、assets文件夹, Manifest优先采用第一个资源包的manifest
   *
   * @param outputStream 合并输出流
   * @param option       合并参数
   * @param pkgs         待合并的资源包
   */
  public static void combine(ZipOutputStream outputStream, Option<ZipFile> option, final ZipFile... pkgs) throws IOException {
    outputStream.setLevel(9);

    //与arsc位置对应的entryMaps
    ResourceFile[] arscs = new ResourceFile[pkgs.length];
    XmlChunk[] manifests = new XmlChunk[pkgs.length];

    final LinkedHashMap<String, ZipFile> combinedEntries = new LinkedHashMap<>();
    final Map<ZipFile, Map<String, String>> zipEntryNameMap = new HashMap<>();
    final Map<ZipFile, Map<String, String>> zipEntryNameReverseMap = new HashMap<>();

    final EntryMapper<ZipFile> entryMapper = option.entryMapper();
    for (int i = 0; i < pkgs.length; i++) {
      ZipFile pkg = pkgs[i];
      Enumeration<? extends ZipEntry> enumeration = pkg.entries();
      while (enumeration.hasMoreElements()) {
        ZipEntry zipEntry = enumeration.nextElement();
        String name = zipEntry.getName();
        if (!isCombineTarget(name, pkg, option.isResJudge(), option.mergeManifest(), option.mergeAssets())) {
          //资源文件之外的东西，只采用第一个包的
          continue;
        }
        if (ARSC_FILE.equals(name)) {
          //稍后再合并
          InputStream inputStream = pkg.getInputStream(zipEntry);
          arscs[i] = ResourceFile.fromInputStream(inputStream);
          IOUtils.closeQuietly(inputStream);
          continue;
        }
        if (MANIFEST_FILE.equals(name)) {
          //稍后再合并
          InputStream inputStream = pkg.getInputStream(zipEntry);
          byte[] data = ByteStreams.toByteArray(inputStream);
          manifests[i] = (XmlChunk) Chunk.newInstance(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
          continue;
        }
        String newName = mapNameIfNeed(name, zipEntry.isDirectory(), entryMapper, pkg);
        if (!newName.equals(name)) {
          Map<String, String> entryNameMap = zipEntryNameMap.get(pkg);
          Map<String, String> entryNameReverseMap = zipEntryNameReverseMap.get(pkg);
          if (entryNameMap == null) {
            entryNameMap = new HashMap<>();
            zipEntryNameMap.put(pkg, entryNameMap);
          }
          if (entryNameReverseMap == null) {
            entryNameReverseMap = new HashMap<>();
            zipEntryNameReverseMap.put(pkg, entryNameReverseMap);
          }
          entryNameMap.put(name, newName);
          entryNameReverseMap.put(newName, name);
          name = newName;
        }
        if (combinedEntries.containsKey(name)) {
          if (!option.allowOverwrite()) {
            if (zipEntry.isDirectory()) {
              //忽略文件夹的重复
              continue;
            }
            throw new IllegalStateException("duplicate entry:" + name + " found!");
          }
        }
        combinedEntries.put(name, pkg);
      }
    }

    byte[] buf = new byte[1024];
    ZipEntry currentEntry = null;
    for (Map.Entry<String, ZipFile> combinedEntry : combinedEntries.entrySet()) {
      String name = combinedEntry.getKey();
      ZipFile zipFile = combinedEntry.getValue();
      Map<String, String> entryNameMap = zipEntryNameMap.get(zipFile);
      Map<String, String> entryNameReverseMap = zipEntryNameReverseMap.get(zipFile);
      if (entryNameReverseMap != null && entryNameReverseMap.containsKey(name)) {
        String originName = entryNameReverseMap.get(name);
        if (!name.equals(originName)) {
          name = originName;
        }
      }

      currentEntry = zipFile.getEntry(name);
      if (currentEntry != null) {
        InputStream inputStream = zipFile.getInputStream(currentEntry);
        ZipEntry outputEntry = ZipUtils.copy(currentEntry);
        if (entryNameMap != null && entryNameMap.containsKey(name)) {
          String newName = entryNameMap.get(name);
          if (!newName.equals(outputEntry.getName())) {
            outputEntry = ZipUtils.copy(currentEntry, newName);
          }
        }
        outputStream.putNextEntry(outputEntry);
        int len;
        while ((len = inputStream.read(buf)) > 0) {
          outputStream.write(buf, 0, len);
        }
        IOUtils.closeQuietly(inputStream);
      }
    }

    List<ResourceFile> arscList = new ArrayList<>();
    for (int i = 0; i < arscs.length; i++) {
      ResourceFile arsc = arscs[i];
      ZipFile pkg = pkgs[i];
      if (arsc != null) {
        Map<String, String> transMap = zipEntryNameMap.get(pkg);
        if (transMap != null) {
          ResUtils.modifyResPath(VisitUtils.getResTableFrom(arsc), transMap);
        }
        arscList.add(arsc);
      }
    }

    ResourceFile mergedArsc = mergeArsc(arscList);

    if (mergedArsc != null) {
      ZipEntry zipEntry = new ZipEntry(ARSC_FILE);
      byte[] data = mergedArsc.toByteArray();
      ApkUtils.setNoCompress(zipEntry, data);
      outputStream.putNextEntry(zipEntry);
      outputStream.write(data);
    }

    List<XmlChunk> manifestList = new ArrayList<>();
    for (XmlChunk xmlChunk : manifests) {
      if (xmlChunk != null) {
        manifestList.add(xmlChunk);
      }
    }
    XmlChunk mergedManifest = mergeManifest(manifestList);

    if (mergedManifest != null) {
      ZipEntry zipEntry = new ZipEntry(MANIFEST_FILE);
      zipEntry.setMethod(ZipEntry.DEFLATED);
      byte[] data = mergedManifest.toByteArray();
      outputStream.putNextEntry(zipEntry);
      outputStream.write(data);
    }

    outputStream.flush();
  }

  public interface Option<T> {
    IsRes<T> isResJudge();

    EntryMapper<T> entryMapper();

    boolean allowOverwrite();

    boolean mergeManifest();

    boolean mergeAssets();
  }

  public static class DefaultOption<T> implements Option<T> {
    @Override
    public IsRes<T> isResJudge() {
      return null;
    }

    @Override
    public EntryMapper<T> entryMapper() {
      return null;
    }

    @Override
    public boolean allowOverwrite() {
      return false;
    }

    @Override
    public boolean mergeManifest() {
      return false;
    }

    @Override
    public boolean mergeAssets() {
      return true;
    }
  }

  /**
   * 某些资源的res目录不是res，靠这个判断是否是res
   */
  public interface IsRes<T> {
    boolean isRes(String name, T from);
  }

  /**
   * 转换entry的名字
   */
  public interface EntryMapper<T> {
    String map(String name, boolean isDir, T from);
  }

  private static <T> boolean isCombineTarget(String name, T from, IsRes<T> isRes, boolean mergeManifest, boolean mergeAssets) {
    // 只合并res和arsc文件
    if (name != null) {
      if (RES_DIR.equals(name) || name.startsWith(RES_DIR + PATH_SEPARATOR)
          || (isRes != null && isRes.isRes(name, from))) {
        return true;
      }
      if (mergeAssets && (ASSETS.equals(name) || name.startsWith(ASSETS + PATH_SEPARATOR))) {
        return true;
      }
      if (ARSC_FILE.equals(name)) {
        return true;
      }
      if (mergeManifest && MANIFEST_FILE.equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static void checkMergeFile(File mergeTo, File mergeFrom) {
    if (!mergeTo.isDirectory()) {
      throw new IllegalStateException("merge to work path(" + mergeTo.getAbsolutePath() + ") is not a directory!");
    }
    if (!mergeFrom.isDirectory()) {
      throw new IllegalStateException("merge from work path(" + mergeFrom.getAbsolutePath() + ") is not a directory!");
    }
  }

  /**
   * assets，res
   */
  private static void mergeDir(final File mergeTo, File mergeFrom) throws IOException {
    if (mergeFrom.isFile()) {
      throw new IllegalStateException("mergeFrom is not a dir " + mergeFrom.getAbsolutePath());
    }
    if (mergeTo.isFile()) {
      throw new IllegalStateException("mergeTo is not a dir " + mergeTo.getAbsolutePath());
    }
    if (mergeFrom.isDirectory()) {
      if (!mergeTo.exists()) {
        FileUtils.copyDirectory(mergeFrom, mergeTo);
      } else {
        final String mergeFromRoot = mergeFrom.getAbsolutePath();
        FileUtils.iterate(mergeFrom, new Callback<File>() {
          @Override
          public void onCallback(File src) {
            File moveTo = new File(mergeTo, src.getAbsolutePath().substring(mergeFromRoot.length()));
            if (moveTo.exists()) {
              if (moveTo.length() != src.length()) {
                //认为是不同的文件
                throw new IllegalStateException("merge file " + src.getAbsolutePath() + " conflict with " + moveTo.getAbsolutePath());
              }
            }
            moveTo.getParentFile().mkdirs();
            try {
              FileUtils.copyFile(src, moveTo);
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
          }
        }, true);
      }
    }
  }

  /**
   * 注意，这里会做反向merge。
   * 返向merge对于保持mergeFrom的id更为方便
   */
  private static void mergeArsc(File mergeTo, File mergeFrom, MergeResult mergeResultOut, boolean keepBoth) throws IOException {
    println("start merge arsc");

    if (!mergeFrom.isFile()) {
      if (!mergeFrom.exists()) {
        //don't need merge
        return;
      } else {
        throw new IllegalStateException("mergeFrom(" + mergeFrom.getAbsolutePath() + ") is not a file when mergeArsc");
      }
    }
    if (!mergeTo.isFile()) {
      if (!mergeTo.exists()) {
        //just copy when mergeTo don't exist
        FileUtils.copyFile(mergeFrom, mergeTo);
        return;
      } else {
        throw new IllegalStateException("mergeTo(" + mergeTo.getAbsolutePath() + ") is not a file when mergeArsc");
      }
    }

    File originMergeTo = mergeTo;
    File tmpFrom = FileUtils.getTempFileFor(mergeFrom);
    try {
      FileUtils.copyFile(mergeFrom, tmpFrom);

      //交换位置，反向merge更方便
      File tmpTo = mergeTo;
      mergeTo = tmpFrom;
      mergeFrom = tmpTo;


      DataInputStream inputStream = new DataInputStream(new FileInputStream(mergeTo));
      ResourceFile mergeToResFile = ResourceFile.fromInputStream(inputStream);
      IOUtils.closeQuietly(inputStream);

      inputStream = new DataInputStream(new FileInputStream(mergeFrom));
      ResourceFile mergeFromResFile = ResourceFile.fromInputStream(inputStream);
      IOUtils.closeQuietly(inputStream);

      mergeResourceTable(VisitUtils.getResTableFrom(mergeToResFile), VisitUtils.getResTableFrom(mergeFromResFile), mergeResultOut, keepBoth);

      //rewrite arsc
      FileUtils.forceDelete(mergeTo);
      DataOutputStream output = new DataOutputStream(new FileOutputStream(mergeTo));
      output.write(mergeToResFile.toByteArray());
      output.flush();
      IOUtils.closeQuietly(output);

      FileUtils.forceDelete(originMergeTo);
      FileUtils.copyFile(mergeTo, originMergeTo);
    } finally {
      tmpFrom.delete();
    }
  }

  private static void mergeManifest(File mergeTo, File mergeFrom) {
    //TODO 暂时不需要合并
  }

  private static ResourceFile mergeArsc(List<ResourceFile> arscList) throws IOException {
    ResourceFile mergedArsc = null;
    if (!arscList.isEmpty()) {
      mergedArsc = arscList.get(0);
      MergeResult mergeResult = new MergeResult();
      ResourceTableChunk resourceTableChunk = VisitUtils.getResTableFrom(mergedArsc);
      if (arscList.size() > 1) {
        for (int i = 1; i < arscList.size(); i++) {
          mergeResourceTable(resourceTableChunk, VisitUtils.getResTableFrom(arscList.get(i)), mergeResult, true);
        }
      }
      MergeHandler.clearDuplicateStrings(resourceTableChunk);
    } else {
      mergedArsc = null;
    }
    return mergedArsc;
  }

  private static XmlChunk mergeManifest(List<XmlChunk> manifests) {
    XmlChunk mergedManifest = null;
    if (!manifests.isEmpty()) {
      mergedManifest = manifests.get(0);
      if (manifests.size() > 1) {
        for (int i = 1; i < manifests.size(); i++) {
          mergedManifest = ManifestMerger.mergeManifest(mergedManifest, manifests.get(i));
        }
      }
    }
    return mergedManifest;
  }

  /**
   * 主要做两件事：
   * 1. merge StringPool，这里面存储有所有string资源，通过index来访问
   * 2. merge PackageChunk, 这活比较重
   * <p>
   * mergeTo: 需要keep id的merge对象
   * mergeFrom: 将内容插入到mergeTo中
   */
  private static void mergeResourceTable(ResourceTableChunk mergeTo, ResourceTableChunk mergeFrom, MergeResult mergeResultOut, boolean keepBoth) throws IOException {
    mergeStringPool(mergeTo, mergeFrom);

    Map<Integer, PackageChunk> mergeToPkgMap = new HashMap<>();
    for (PackageChunk mergeToPkg : mergeTo.getPackages()) {
      mergeToPkgMap.put(mergeToPkg.getId(), mergeToPkg);
    }

    //merge from的资源id变动记录，需要根据这个修改R.java和引用类型资源
    Map<Integer, Integer> mfIdMapping = new HashMap<>();

    /**
     * merge PackageChunk
     */
    for (PackageChunk mergeFromPkg : mergeFrom.getPackages()) {
      PackageChunk mergeToPkg = mergeToPkgMap.get(mergeFromPkg.getId());
      if (mergeToPkg != null) {
        //相同id的packageChunk存在，合并
        if (keepBoth) {
          combinePackageChunk(mergeToPkg, mergeFromPkg, true);
        } else {
          mfIdMapping.putAll(mergePackageChunk(mergeToPkg, mergeFromPkg));
        }

//                println("merged pkg:" + mergeToPkg.packageName);
//                for (Chunk c : mergeToPkg.chunks.values()) {
//                    if (c == mergeToPkg.getTypeStringPool()) {
//                        println("TypeStringPool:" + c.getClass().getSimpleName());
//                        for (String s : ((StringPoolChunk) c).strings) {
//                            println("type:" + s);
//                        }
//                    } else if (c instanceof TypeSpecChunk) {
//                        println("TypeSpecChunk(" + Integer.toHexString(((TypeSpecChunk) c).getId()) + "):"
//                            + mergeToPkg.getTypeStringPool().getString(((TypeSpecChunk) c).getId() - 1));
//                    } else if (c instanceof TypeChunk) {
//                        println("TypeChunk(" + Integer.toHexString(((TypeChunk) c).getId()) + "):" + ((TypeChunk) c).getTypeName());
//                    } else {
//                        println(c.getClass().getSimpleName());
//                    }
//                }
      } else {
        //不存在相同id的packageChunk，直接append到ResourceTable中
        mergeTo.appendPackageChunk(mergeFromPkg);
      }
    }

    mergeResultOut.idMapping = mfIdMapping;
  }

  private static void mergeStringPool(ResourceTableChunk mergeTo, ResourceTableChunk mergeFrom) {
    final StringPoolChunk mtStringPool = mergeTo.getStringPool();
    final StringPoolChunk mfStringPool = mergeFrom.getStringPool();

    final int mfStyleCount = mfStringPool.getStyleCount();
    if (mfStyleCount == 0) {
      //如果mergeFrom没有style资源，那么简单合并就行，去重在后面进行
      final int stringOffset = mtStringPool.strings.size();
      mtStringPool.strings.addAll(mfStringPool.strings);
      for (PackageChunk pkg : mergeFrom.getPackages()) {
        VisitUtils.visitResourceValue(pkg, new VisitUtils.Visitor<ResourceValue>() {
          @Override
          public void onVisit(ResourceValue resourceValue) {
            addStringPoolOffset(resourceValue, stringOffset);
          }
        });
      }
    } else {
//      System.out.println("mt strings size: " + mtStringPool.strings.size() + " content:" + Arrays.toString(mtStringPool.strings.toArray()));
//      System.out.println("mt spans size:" + mtStringPool.styles.size() + " content:" + Arrays.toString(mtStringPool.styles.toArray()));
//
//      System.out.println("mf strings size: " + mfStringPool.strings.size() + " content:" + Arrays.toString(mfStringPool.strings.toArray()));
//      System.out.println("mf spans size:" + mfStringPool.styles.size() + " content:" + Arrays.toString(mfStringPool.styles.toArray()));

      List<StringPoolChunk.StringPoolStyle> mtStyles = new ArrayList<>();
      mtStyles.addAll(mtStringPool.styles);

      List<StringPoolChunk.StringPoolStyle> mfStyles = new ArrayList<>();
      List<String> mfStyledStrings = new ArrayList<>();
      for (int i = 0; i < mfStyleCount; i++) {
        mfStyles.add(mfStringPool.getStyle(i));
        mfStyledStrings.add(mfStringPool.getString(i));
      }
      mtStringPool.strings.addAll(0, mfStyledStrings);
      final int mfStringOffset = mtStringPool.strings.size();

      mtStringPool.strings.addAll(mfStringPool.strings);

      mtStringPool.styles.addAll(0, mfStyles);

      for (PackageChunk pkg : mergeTo.getPackages()) {
        VisitUtils.visitResourceValue(pkg, new VisitUtils.Visitor<ResourceValue>() {
          @Override
          public void onVisit(ResourceValue resourceValue) {
            addStringPoolOffset(resourceValue, mfStyleCount);
          }
        });
      }

      for (PackageChunk pkg : mergeFrom.getPackages()) {
        VisitUtils.visitResourceValue(pkg, new VisitUtils.Visitor<ResourceValue>() {
          @Override
          public void onVisit(ResourceValue value) {
            if (value != null && value.type() == ResourceValue.Type.STRING) {
              int index = value.data();
              if (index < mfStyleCount) {
                //这里的位置和原来一样，不需要改
                return;
              }
              addStringPoolOffset(value, mfStringOffset);
            }
          }
        });
      }

      for (StringPoolChunk.StringPoolStyle style : mtStyles) {
        for (StringPoolChunk.StringPoolSpan span : style.spans()) {
          int nameIndex = span.nameIndex();
          span.setNameIndex(nameIndex + mfStyleCount);
        }
      }

      for (StringPoolChunk.StringPoolStyle style : mfStyles) {
        for (StringPoolChunk.StringPoolSpan span : style.spans()) {
          int nameIndex = span.nameIndex();
          span.setNameIndex(nameIndex + mfStringOffset);
          span.setParent(mtStringPool);
        }
      }
    }
  }

  private static void clearDuplicateStrings(ResourceTableChunk tableChunk) {
    final StringPoolChunk stringPool = tableChunk.getStringPool();

    final Map<Object, String> stringRefMap = new HashMap<>();
    final Map<ResourceValue, StringPoolChunk.StringPoolStyle> spanRefMap = new HashMap<>();

    // 收集span里面引用的string
    for (StringPoolChunk.StringPoolStyle style : stringPool.styles) {
      for (StringPoolChunk.StringPoolSpan span : style.spans()) {
        stringRefMap.put(span, stringPool.getString(span.nameIndex()));
      }
    }

    for (PackageChunk pkg : tableChunk.getPackages()) {
      VisitUtils.visitResourceValue(pkg, new VisitUtils.Visitor<ResourceValue>() {
        @Override
        public void onVisit(ResourceValue value) {
          if (value != null && value.type() == ResourceValue.Type.STRING) {
            int index = value.data();
            stringRefMap.put(value, stringPool.getString(index));
            // 有styles的string，总是排在前面
            if (index < stringPool.getStyleCount()) {
              spanRefMap.put(value, stringPool.getStyle(index));
            }
          }
        }
      });
    }

    final List<StringPoolChunk.StringPoolStyle> styles = new ArrayList<>();
    final List<String> mergedStrings = new ArrayList<>();

    //先收集styled strings，放在StringPool前面，String保证与Style对齐
    List<String> styledStrings = new ArrayList<>();
    int styleCount = stringPool.getStyleCount();
    for (int i = 0; i < styleCount; i++) {
      styledStrings.add(stringPool.getString(i));
      styles.add(stringPool.getStyle(i));
    }

    //然后收集没有style的strings，并去重
    LinkedHashSet<String> unStyledStrings = new LinkedHashSet<>();
    for (int i = styleCount; i < stringPool.getStringCount(); i++) {
      unStyledStrings.add(stringPool.getString(i));
    }

    //依次添加styled strings, un styled strings，完成strings合并
    mergedStrings.addAll(styledStrings);
    mergedStrings.addAll(unStyledStrings);

    stringPool.strings.clear();
    stringPool.strings.addAll(mergedStrings);

    for (PackageChunk pkg : tableChunk.getPackages()) {
      VisitUtils.visitResourceValue(pkg, new VisitUtils.Visitor<ResourceValue>() {
        @Override
        public void onVisit(ResourceValue value) {
          if (value != null && value.type() == ResourceValue.Type.STRING) {
            int index = -1;
            StringPoolChunk.StringPoolStyle style = spanRefMap.get(value);
            if (style != null) {
              for (int i = 0; i < styles.size(); i++) {
                if (styles.get(i) == style) {
                  index = i;
                  break;
                }
              }
            } else {
              if (!stringRefMap.containsKey(value)) {
                throw new IllegalStateException("can't find string for ResourceValue " + value);
              }
              String string = stringRefMap.get(value);
              index = mergedStrings.lastIndexOf(string);
            }

            if ( index < 0 ){
              throw new IllegalStateException("can't find string for ResourceValue " + value);
            }
            value.setData(index);
          }
        }
      });
    }

    for (StringPoolChunk.StringPoolStyle style : styles) {
      for (StringPoolChunk.StringPoolSpan span : style.spans()) {
        String s = stringRefMap.get(span);
        if (s == null) {
          throw new IllegalStateException("can't find string for span " + span);
        }
        int index = mergedStrings.lastIndexOf(s);
        span.setNameIndex(index);
      }
    }

//    System.out.println("strings:" + Arrays.toString(stringPool.strings.toArray()));
//    System.out.println("spans:" + Arrays.toString(stringPool.styles.toArray()));
  }

  //将访问stringPool的index加上offset
  private static void addStringPoolOffset(ResourceValue value, int stringPoolOffset) {
    if (value != null && value.type() == ResourceValue.Type.STRING) {
      value.setData(value.data() + stringPoolOffset);
    }
  }

  private static void combinePackageChunk(PackageChunk combineTo, PackageChunk combineFrom, boolean overwriteType) throws IOException {
    if (combineTo.getId() != combineFrom.getId()) {
      throw new IllegalArgumentException("can't combine the PackageChunk they has different package id");
    }
    //merge key strings
    final int keyOffset = combineTo.getKeyStringPool().strings.size();
    List<String> tmp = new ArrayList<>();
    tmp.addAll(combineTo.getKeyStringPool().strings);
    combineTo.getKeyStringPool().strings.addAll(combineFrom.getKeyStringPool().strings);
    combineFrom.getKeyStringPool().strings.addAll(0, tmp);
    //combineFrom的keyStrings位置发生了变动，所以combineFrom的keyIndex需要加offset
    VisitUtils.visitEntry(combineFrom, new VisitUtils.Visitor<TypeChunk.Entry>() {
      @Override
      public void onVisit(TypeChunk.Entry entry) {
        entry.setKeyIndex(entry.keyIndex() + keyOffset);
      }
    });

    int maxTypeId = Math.max(getMaxTypeId(combineTo), getMaxTypeId(combineFrom));
    List<String> typeStrings = new ArrayList<>();
    LinkedHashMap<Integer, TypeChunkPair> combinedTypeChunksMap = new LinkedHashMap<>();
    for (int i = 1; i <= maxTypeId; i++) {
      TypeSpecChunk typeSpecChunk = null;
      PackageChunk typeChunksPkg = null;
      if (overwriteType) {
        typeSpecChunk = combineFrom.getTypeSpecChunk(i);
        if (typeSpecChunk != null) {
          typeChunksPkg = combineFrom;
        } else {
          typeSpecChunk = combineTo.getTypeSpecChunk(i);
          typeChunksPkg = combineTo;
        }
      } else {
        typeSpecChunk = combineTo.getTypeSpecChunk(i);
        if (typeSpecChunk != null) {
          typeChunksPkg = combineTo;
        } else {
          typeSpecChunk = combineFrom.getTypeSpecChunk(i);
          typeChunksPkg = combineFrom;
        }
      }
      if (typeSpecChunk != null) {
        if (combinedTypeChunksMap.put(i, new TypeChunkPair(typeSpecChunk, typeChunksPkg.getTypeChunks(i))) != null) {
          throw new IllegalStateException("there has same type when combinePackageChunk, typeId:" + typeSpecChunk.id);
        }
        typeStrings.add(typeChunksPkg.getTypeStringPool().getString(i - 1));
      } else {
        typeStrings.add("<empty>");
      }
    }

    combineTo.getTypeStringPool().strings.clear();
    combineTo.getTypeStringPool().strings.addAll(typeStrings);

    //记录下typeStrings和keyStrings调整后的实际位置，等替换chunks后再设置，否则PackageChunk替换过程中找不到StringPoolChunk
    int newTypeStringsOffset;
    int newKeyStringsOffset;

    //按照排好的顺序来merge chunk (TypeStringPool -> KeyStringPool -> (TypeSpecChunk->TypeChunk->TypeChunk) -> (TypeSpecChunk->TypeChunk->TypeChunk)...-> LibraryChunk)
    Map<Integer, Chunk> combinedChunks = new LinkedHashMap<>();

    int offset = combineTo.offset + combineTo.getHeaderSize();
    combinedChunks.put(offset, combineTo.getTypeStringPool());
    newTypeStringsOffset = offset - combineTo.offset;
    offset += combineTo.getTypeStringPool().toByteArray().length;

    combinedChunks.put(offset, combineTo.getKeyStringPool());
    newKeyStringsOffset = offset - combineTo.offset;
    offset += combineTo.getKeyStringPool().toByteArray().length;

    List<TypeChunkPair> allTypes = new ArrayList<>(combinedTypeChunksMap.values());
    Collections.sort(allTypes, new Comparator<TypeChunkPair>() {
      @Override
      public int compare(TypeChunkPair t1, TypeChunkPair t2) {
        return Integer.compare(t1.mTypeSpecChunk.id, t2.mTypeSpecChunk.id);
      }
    });

    combineTo.getTypeSpecChunks().clear();
    combineTo.getTypeChunks().clear();

    for (TypeChunkPair typeChunkPair : allTypes) {
      //重写chunks组成，这个会回写到文件中
      combinedChunks.put(offset, typeChunkPair.mTypeSpecChunk);
      offset += typeChunkPair.mTypeSpecChunk.getOriginalChunkSize();
      if (typeChunkPair.mTypeChunks != null) {
        for (TypeChunk typeChunk : typeChunkPair.mTypeChunks) {
          combinedChunks.put(offset, typeChunk);
          offset += typeChunk.getOriginalChunkSize();
        }
      }
      //重写TypeSpecChunk和TypeChunk组成，方便下次merge
      combineTo.getTypeSpecChunks().put(typeChunkPair.mTypeSpecChunk.getId(), typeChunkPair.mTypeSpecChunk);
      combineTo.getTypeChunks().put(typeChunkPair.mTypeSpecChunk.getId(), typeChunkPair.mTypeChunks);
    }

    LibraryChunk libraryChunk = combineTo.getLibrary();
    if (libraryChunk == null) {
      libraryChunk = createLibraryChunk(combineTo.getId(), combineTo.getPackageName());
    }
    combinedChunks.put(offset, libraryChunk);
    offset += libraryChunk.getOriginalChunkSize();

    combineTo.chunks = combinedChunks;
    combineTo.typeStringsOffset = newTypeStringsOffset;
    combineTo.keyStringsOffset = newKeyStringsOffset;
  }

  private static LibraryChunk createLibraryChunk(int customPackageId, String packageName) {
    final short headerSize = 12; //type(2) + headerSize(2) + chunkSize(4) + entrySize(4)
    final int libChunkSize = headerSize + LibraryChunk.Entry.SIZE; //headerSize + 1 * Entry.SIZE
    ByteBuffer libByteBuffer = ByteBuffer.allocate(libChunkSize);
    libByteBuffer.putShort(Chunk.Type.TABLE_LIBRARY.code()); //type
    libByteBuffer.putShort(headerSize); //headerSize
    libByteBuffer.putInt(libChunkSize); //chunkSize
    libByteBuffer.putInt(1); //entrySize
    libByteBuffer.putInt(customPackageId); //packageId
    PackageUtils.writePackageName(libByteBuffer, packageName);
    libByteBuffer.rewind();

    return (LibraryChunk) Chunk.newInstance(libByteBuffer);
  }

  private static class TypeChunkPair {
    TypeSpecChunk mTypeSpecChunk;
    List<TypeChunk> mTypeChunks;

    TypeChunkPair(TypeSpecChunk typeSpecChunk, List<TypeChunk> typeChunks) {
      mTypeSpecChunk = typeSpecChunk;
      mTypeChunks = typeChunks;
    }
  }

  private static int getMaxTypeId(PackageChunk pkg) {
    int currentMax = 0;
    for (int typeId : pkg.getTypeSpecChunks().keySet()) {
      if (currentMax < typeId) {
        currentMax = typeId;
      }
    }
    return currentMax;
  }

  /**
   * 合并PackageChunk，将mergeFrom的内容插入到mergeTo中，mergeTo原资源id需要保持不变（类似public）
   * 也就是mergeFrom的资源entry只能排在mergeTo的相同类型资源entry后面
   * <p>
   * 对于typeStrings, 将mergeFrom的typeStrings插入到mergeTo对应位置，但是需要记录type有变动的位置，做一个mapping
   * 对于keyStrings，将mergeFrom的keyStrings插入到mergeTo的后面。所以mergeFrom的entry的keyIndex需要加上一个offset, offset = mergeTo.keyStrings.size()
   * <p>
   * 最后返回mapping文件，里面有修改前和修改之后的id映射表
   */
  private static Map<Integer, Integer> mergePackageChunk(PackageChunk mergeTo, PackageChunk mergeFrom) throws IOException {
    if (mergeTo.getId() != mergeFrom.getId()) {
      throw new IllegalArgumentException("can't merge the PackageChunk they has different package id");
    }

    final Map<Integer, Integer> mfIdMapping = new HashMap<>();

    //先缓存TypeChunk和TypeSpecChunk，后期数据结构变化之后，还能根据typeName来访问
    Map<String, Collection<TypeChunk>> mtTypesCache = new HashMap<>();
    Map<String, TypeSpecChunk> mtSpecCache = new HashMap<>();
    for (String s : mergeTo.getTypeStringPool().strings) {
      List<TypeChunk> clone = new ArrayList<>();
      Collection<TypeChunk> typeChunks = mergeTo.getTypeChunks(s);
      if (typeChunks != null) {
        clone.addAll(typeChunks);
      }
      mtTypesCache.put(s, clone);
      mtSpecCache.put(s, mergeTo.getTypeSpecChunk(s));
    }
    Map<String, Collection<TypeChunk>> mfTypesCache = new HashMap<>();
    Map<String, TypeSpecChunk> mfSpecCache = new HashMap<>();
    for (String s : mergeFrom.getTypeStringPool().strings) {
      List<TypeChunk> clone = new ArrayList<>();
      Collection<TypeChunk> typeChunks = mergeFrom.getTypeChunks(s);
      if (typeChunks != null) {
        clone.addAll(typeChunks);
      }
      mfTypesCache.put(s, clone);
      mfSpecCache.put(s, mergeFrom.getTypeSpecChunk(s));
    }

    //当前正在编译的是mergeFrom，所以包名应采用mergeFrom的
    mergeTo.packageName = mergeFrom.packageName;

    //merge key strings
    final int keyOffset = mergeTo.getKeyStringPool().strings.size();
    List<String> tmp = new ArrayList<>();
    tmp.addAll(mergeTo.getKeyStringPool().strings);
    mergeTo.getKeyStringPool().strings.addAll(mergeFrom.getKeyStringPool().strings);
    mergeFrom.getKeyStringPool().strings.addAll(0, tmp);
    //mergeFrom的keyStrings位置发生了变动，所以mergeFrom的keyIndex需要加offset
    VisitUtils.visitEntry(mergeFrom, new VisitUtils.Visitor<TypeChunk.Entry>() {
      @Override
      public void onVisit(TypeChunk.Entry entry) {
        entry.setKeyIndex(entry.keyIndex() + keyOffset);
      }
    });

    //merge type strings
    LinkedHashSet<String> mergedTypeSet = new LinkedHashSet<>();
    mergedTypeSet.addAll(mergeTo.getTypeStringPool().strings);
    mergedTypeSet.addAll(mergeFrom.getTypeStringPool().strings);
    mergeTo.getTypeStringPool().strings.clear();
    mergeTo.getTypeStringPool().strings.addAll(mergedTypeSet);

    //记录下typeStrings和keyStrings调整后的实际位置，等替换chunks后再设置，否则PackageChunk替换过程中找不到StringPoolChunk
    int newTypeStringsOffset;
    int newKeyStringsOffset;

    //按照排好的顺序来merge chunk (TypeStringPool -> KeyStringPool -> (TypeSpecChunk->TypeChunk->TypeChunk) -> (TypeSpecChunk->TypeChunk->TypeChunk)...-> LibraryChunk)
    Map<Integer, Chunk> mergedChunks = new LinkedHashMap<>();

    int offset = mergeTo.offset + mergeTo.getHeaderSize();
    mergedChunks.put(offset, mergeTo.getTypeStringPool());
    newTypeStringsOffset = offset - mergeTo.offset;
    offset += mergeTo.getTypeStringPool().toByteArray().length;

    mergedChunks.put(offset, mergeTo.getKeyStringPool());
    newKeyStringsOffset = offset - mergeTo.offset;
    offset += mergeTo.getKeyStringPool().toByteArray().length;

    //收集所有的merge from TypeChunk，方便修改其中引用的resId
    Set<TypeChunk> mfAllTypeChunks = new HashSet<>();

    //typeId是1-based
    int typeId = 1;
    for (String typeName : mergedTypeSet) {
      TypeSpecChunk tscMt = mtSpecCache.get(typeName);
      TypeSpecChunk tscMf = mfSpecCache.get(typeName);
      if (tscMf != null && tscMt != null) {
        println("merge type:" + typeName + " typeId:" + typeId + " mt" + tscMt.getId() + ":" + mergeTo.getTypeStringPool().getString(tscMt.id - 1)
            + " mf" + tscMf.getId() + ":" + mergeFrom.getTypeStringPool().getString(tscMf.id - 1));
        //先merge TypeSpecChunk
        tscMt.id = typeId;
        mergeTypeSpecChunk(tscMt, tscMf);
        mergedChunks.put(offset, tscMt);
        //TypeSpecChunk已被修改，需要重新计算length
        offset += tscMt.toByteArray().length;
        mergeTo.getTypeSpecChunks().put(typeId, tscMt);

        //然后再merge TypeChunk
        List<TypeChunk> typeChunksMt = new ArrayList<>();
        typeChunksMt.addAll(mtTypesCache.get(typeName));
        List<TypeChunk> typeChunksMf = new ArrayList<>();
        typeChunksMf.addAll(mfTypesCache.get(typeName));
        Map<Integer, Integer> entryIdMap = mergeTypeChunks(tscMt.getResourceCount(), typeChunksMt, typeChunksMf);
        mfAllTypeChunks.addAll(typeChunksMf);

        mergeTo.getTypeChunks().remove(typeId);
        for (TypeChunk c : typeChunksMt) {
          c.id = typeId;
          c.parent = mergeTo;
          mergedChunks.put(offset, c);
          offset += c.toByteArray().length;
        }
        List<TypeChunk> typeChunkList = mergeTo.getTypeChunks().get(typeId);
        if (typeChunkList == null) {
          typeChunkList = new ArrayList<>();
          mergeTo.getTypeChunks().put(typeId, typeChunkList);
        }
        typeChunkList.addAll(typeChunksMt);

        //写mergeFrom idMapping, 之后要根据这个调整R.java和引用资源的id
        int originTypeId = tscMf.id;
        int moveToTypeId = typeId;
        for (Map.Entry<Integer, Integer> eMapEntry : entryIdMap.entrySet()) {
          int originResId = (mergeFrom.getId() << 24) | (originTypeId << 16) | eMapEntry.getKey();
          int targetResId = (mergeFrom.getId() << 24) | (moveToTypeId << 16) | eMapEntry.getValue();
          mfIdMapping.put(originResId, targetResId);
        }
      } else if (tscMf != null) {
        println("merge type:" + typeName + " typeId:" + typeId
            + " mf" + tscMf.getId() + ":" + mergeFrom.getTypeStringPool().getString(tscMf.id - 1));
        tscMf.id = typeId;
        mergedChunks.put(offset, tscMf);
        offset += tscMf.getOriginalChunkSize();

        tscMf.parent = mergeTo;
        mergeTo.getTypeSpecChunks().put(typeId, tscMf);

        mergeTo.getTypeChunks().remove(typeId);
        for (TypeChunk c : mfTypesCache.get(typeName)) {
          //写mergeFrom idMapping, 之后要根据这个调整R.java和引用资源的id
          for (Map.Entry<Integer, TypeChunk.Entry> mapEntry : c.getEntries().entrySet()) {
            if (mapEntry.getValue() != null) {
              int originResId = (mergeFrom.getId() << 24) | (c.id << 16) | mapEntry.getKey();
              int moveToTypeId = (mergeFrom.getId() << 24) | (typeId << 16) | mapEntry.getKey();
              mfIdMapping.put(originResId, moveToTypeId);
            }
          }
          c.id = typeId;
          c.parent = mergeTo;
          mergedChunks.put(offset, c);
          offset += c.getOriginalChunkSize();

          List<TypeChunk> typeChunkList = mergeTo.getTypeChunks().get(typeId);
          if (typeChunkList == null) {
            typeChunkList = new ArrayList<>();
            mergeTo.getTypeChunks().put(typeId, typeChunkList);
          }
          typeChunkList.add(c);

          mfAllTypeChunks.add(c);
        }
      } else if (tscMt != null) {
        println("merge type:" + typeName + " typeId:" + typeId + " mt" + tscMt.getId() + ":" + mergeTo.getTypeStringPool().getString(tscMt.id - 1));
        tscMt.id = typeId;
        mergedChunks.put(offset, tscMt);
        offset += tscMt.getOriginalChunkSize();

        mergeTo.getTypeSpecChunks().put(typeId, tscMt);

        mergeTo.getTypeChunks().remove(typeId);
        for (TypeChunk c : mtTypesCache.get(typeName)) {
          c.id = typeId;
          mergedChunks.put(offset, c);
          offset += c.getOriginalChunkSize();
          List<TypeChunk> typeChunkList = mergeTo.getTypeChunks().get(typeId);
          if (typeChunkList == null) {
            typeChunkList = new ArrayList<>();
            mergeTo.getTypeChunks().put(typeId, typeChunkList);
          }
          typeChunkList.add(c);
        }
      } else {
        throw new IllegalStateException("both TypeChunk is null when merge " + typeName);
      }
      typeId++;
    }

    if (mergeFrom.getLibrary() != null) {
      //这里不同，这里需要保留最终apk的LibraryChunk
      mergedChunks.put(offset, mergeFrom.getLibrary());
    }

    if (!mfAllTypeChunks.isEmpty()
        && !mfIdMapping.isEmpty()) {
      for (TypeChunk chunk : mfAllTypeChunks) {
        for (TypeChunk.Entry entry : chunk.getEntries().values()) {
          if (entry != null) {
            ResIdUtils.modifyRefResourceId(entry, new ResIdUtils.Modifier() {
              @Override
              public int onModify(int originResId, int type) {
                Integer mapping = mfIdMapping.get(originResId);
                if (mapping == null) {
                  return originResId;
                }
                return mapping;
              }
            });
          }
        }
      }
    }
    mergeTo.chunks = mergedChunks;
    mergeTo.typeStringsOffset = newTypeStringsOffset;
    mergeTo.keyStringsOffset = newKeyStringsOffset;

    return mfIdMapping;
  }

  /**
   * TypeSpecChunk里面的resources里表示的是每个对应位置entry的拥有的资源维度。
   * 比如String TypeSpecChunk的resources[0], 表示的是String type的entryId为0x0000的资源所拥有的各种资源维度。
   * 因为mergeTo的entry位置并不会发生改变，这里将mergeFrom的resources插入到mergeTo.resources后即可。
   * 所以mergeFrom的TypeChunk的entry位置需要后移mergeTo.resourceCount个身位
   */
  private static void mergeTypeSpecChunk(TypeSpecChunk mergeTo, TypeSpecChunk mergeFrom) {
    int[] resources = new int[mergeTo.getResourceCount() + mergeFrom.getResourceCount()];
    System.arraycopy(mergeTo.resources, 0, resources, 0, mergeTo.resources.length);
    System.arraycopy(mergeFrom.resources, 0, resources, mergeTo.resources.length, mergeFrom.resources.length);
    mergeTo.resources = resources;
  }

  /**
   * merge相同type的chunks；
   * 每个TypeChunk的entryCount需要与对应的TypeSpecChunk的resourceCount相同；
   * 对于mergeTo TypeChunk来说，entry位置不变。mergeFrom TypeChunk的Entry位置需要后移。
   *
   * @return mergeFrom中Entry id移动前和移动后的映射
   */
  private static Map<Integer, Integer> mergeTypeChunks(int mergedResourceCount, List<TypeChunk> mergeTo, List<TypeChunk> mergeFrom) {
    Map<Integer, Integer> entryMap = null;
    for (TypeChunk mt : mergeTo) {
      mt.entryCount = mergedResourceCount;
    }
    for (TypeChunk mf : mergeFrom) {
      entryMap = expandEntryCount(mf, mergedResourceCount);
    }
    mergeTo.addAll(mergeFrom);

    if (entryMap == null) {
      entryMap = Collections.emptyMap();
    }
    return entryMap;
  }

  private static Map<Integer, Integer> expandEntryCount(TypeChunk mf, int count) {
    if (mf.entryCount > count) {
      throw new IllegalStateException("can't expand TypeChunk(" + mf.getClass().getSimpleName() + ") entryCount, " +
          "because entryCount(" + mf.entryCount + ") > expandCount(" + count + ")!");
    }
    Map<Integer, Integer> entryMap = new HashMap<>();
    Map<Integer, TypeChunk.Entry> originCopy = new HashMap<>();
    originCopy.putAll(mf.getEntries());
    for (int i = 0; i < mf.entryCount; i++) {
      int moveToIndex = i + (count - mf.entryCount);
      entryMap.put(i, moveToIndex);
      mf.getEntries().put(moveToIndex, originCopy.get(i));
    }
    for (int i = 0; i < count - mf.entryCount; i++) {
      mf.getEntries().put(i, null);
    }
    mf.entryCount = count;
    return entryMap;
  }

  public static class MergeResult {
    public Map<Integer, Integer> idMapping;
  }
}
