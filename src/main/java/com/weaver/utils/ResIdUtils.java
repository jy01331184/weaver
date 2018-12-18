package com.weaver.utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pink.madis.apk.arsc.Chunk;
import pink.madis.apk.arsc.ChunkWithChunks;
import pink.madis.apk.arsc.PackageChunk;
import pink.madis.apk.arsc.ResourceFile;
import pink.madis.apk.arsc.ResourceTableChunk;
import pink.madis.apk.arsc.ResourceValue;
import pink.madis.apk.arsc.TypeChunk;
import pink.madis.apk.arsc.XmlAttribute;
import pink.madis.apk.arsc.XmlResourceMapChunk;
import pink.madis.apk.arsc.XmlStartElementChunk;

/**
 * 修改resId使用的工具
 *
 * Created by zhoulei on 17/7/24.
 */
public class ResIdUtils {
    public interface Modifier {
        int TYPE_BAG_KEY = 1;
        int TYPE_ENTRY_PARENT = 2;
        int TYPE_RESOURCES_VALUE_REF = 3;
        int TYPE_XML_RESOURCES_VALUE_REF = 4;
        int TYPE_XML_RESOURCE_MAP = 5;

        int onModify(int originResId, int type);
    }

    private static final Set<Integer> TYPE_ALL = new HashSet<>();
    static {
        TYPE_ALL.add(Modifier.TYPE_BAG_KEY);
        TYPE_ALL.add(Modifier.TYPE_ENTRY_PARENT);
        TYPE_ALL.add(Modifier.TYPE_RESOURCES_VALUE_REF);
        TYPE_ALL.add(Modifier.TYPE_XML_RESOURCES_VALUE_REF);
        TYPE_ALL.add(Modifier.TYPE_XML_RESOURCE_MAP);
    }

    public static void modifyRefResourceId(File appDir, Modifier modifier) throws IOException {
        modifyRefResourceId(appDir, modifier, TYPE_ALL);
    }

    public static void modifyRefResourceId(File appDir, final Modifier modifier, Set<Integer> types) throws IOException {
        modifyRefResourceId(appDir, null, modifier, types);
    }

    public static void modifyRefResourceId(File appDir, File resDir, Modifier modifier) throws IOException {
        modifyRefResourceId(appDir, resDir, modifier, TYPE_ALL);
    }

    /**
     * 修改aapt生成的资源中，所有被引用的ResourceId
     *
     * 包括：
     * 1. ResourceTable中，entry的parent持有的resId引用
     * 2. ResourceTable和XmlStartElementChunk中，attr型的ResourceValue持有的resId引用
     * 3. XmlResourceMapChunk持有的resId mapping
     *
     * @param appDir apk解包之后的路径
     * @param resDir 制定resDir的路径，因为res目录的路径可变
     * @param modifier
     */
    public static void modifyRefResourceId(File appDir, File resDir, final Modifier modifier, Set<Integer> types) throws IOException {
        modifyResources(appDir,
                resDir,
                (types.contains(Modifier.TYPE_ENTRY_PARENT) || types.contains(Modifier.TYPE_BAG_KEY)) ? new VisitUtils.Visitor<TypeChunk.Entry>() {
                    @Override
                    public void onVisit(TypeChunk.Entry entry) {
                        //entry需要修改parent引用和bagKey引用
                        modifyRefResourceId(entry, modifier);
                    }
                } : null,
                types.contains(Modifier.TYPE_RESOURCES_VALUE_REF) ? new VisitUtils.Visitor<ResourceValue>() {
                    @Override
                    public void onVisit(ResourceValue resourceValue) {
                        modifyResourceValueIfRef(resourceValue, modifier);
                    }
                } : null,
                types.contains(Modifier.TYPE_XML_RESOURCES_VALUE_REF) ? new VisitUtils.Visitor<ResourceValue>() {
                    @Override
                    public void onVisit(ResourceValue resourceValue) {
                        ResourceValue.Type type = resourceValue.type();
                        //修改引用类型的ResourceValue的值
                        if (type == ResourceValue.Type.REFERENCE || type == ResourceValue.Type.ATTRIBUTE
                                || type == ResourceValue.Type.DYNAMIC_REFERENCE
                                || type == ResourceValue.Type.DYNAMIC_ATTRIBUTE) {
                            resourceValue.setData(modifier.onModify(resourceValue.data(), Modifier.TYPE_XML_RESOURCES_VALUE_REF));
                        }
                    }
                } : null,
                types.contains(Modifier.TYPE_XML_RESOURCE_MAP) ? new VisitUtils.Visitor<XmlResourceMapChunk>() {
                    @Override
                    public void onVisit(XmlResourceMapChunk xmlResourceMapChunk) {
                        //修改XmlResourceMapChunk中的resId map
                        List<Integer> remappedResIds = new ArrayList<>();
                        for (Integer resId : xmlResourceMapChunk.resources) {
                            remappedResIds.add(modifier.onModify(resId, Modifier.TYPE_XML_RESOURCE_MAP));
                        }
                        xmlResourceMapChunk.resources.clear();
                        xmlResourceMapChunk.resources.addAll(remappedResIds);
                    }
                } : null);
    }

    public static void modifyRefResourceId(TypeChunk.Entry entry, Modifier modifier) {
        if (entry.isComplex()) {
            entry.setParentEntry(modifier.onModify(entry.parentEntry(), Modifier.TYPE_ENTRY_PARENT));

            Map<Integer, ResourceValue> bagKeyValueMap = entry.values();
            Map<Integer, ResourceValue> modified = new HashMap<>();
            for (int bagKey : bagKeyValueMap.keySet()) {
                ResourceValue resourceValue = bagKeyValueMap.get(bagKey);
                modifyResourceValueIfRef(resourceValue, modifier);
                modified.put(modifier.onModify(bagKey, Modifier.TYPE_BAG_KEY), resourceValue);
            }

            //保证key是递增的
            List<Integer> keySorted = new ArrayList<>(modified.keySet());
            Collections.sort(keySorted);
            LinkedHashMap<Integer, ResourceValue> sortedValues = new LinkedHashMap<>();
            for (Integer key : keySorted) {
                sortedValues.put(key, modified.get(key));
            }
            entry.setValues(sortedValues);
        } else {
            ResourceValue resourceValue = entry.value();
            modifyResourceValueIfRef(resourceValue, modifier);
        }
    }

    public static int makeResId(int pkgId, int typeId, int entryId) {
        if (pkgId < 0 || pkgId > 0x7f) {
            throw new IllegalArgumentException("packageId " + pkgId + " over range-[0, 127]");
        }
        if (typeId < 0 || typeId > 0xff) {
            throw new IllegalArgumentException("typeId " + typeId + " over range-[1, 255]");
        }
        if (entryId < 0 || entryId > 0xffff) {
            throw new IllegalArgumentException("entryId " + entryId + " over range-[0, 65535]");
        }
        return 0x00000000 | pkgId << 24 | typeId << 16 | entryId;
    }

    public static int getPackageId(int resId) {
        return (0xFF000000 & resId) >>> 24;
    }

    private static void modifyResourceValueIfRef(ResourceValue resourceValue, Modifier modifier) {
        if (resourceValue != null) {
            ResourceValue.Type type = resourceValue.type();
            //修改引用类型的ResourceValue的值
            if (type == ResourceValue.Type.REFERENCE || type == ResourceValue.Type.ATTRIBUTE
                || type == ResourceValue.Type.DYNAMIC_REFERENCE
                || type == ResourceValue.Type.DYNAMIC_ATTRIBUTE) {
                resourceValue.setData(modifier.onModify(resourceValue.data(), Modifier.TYPE_RESOURCES_VALUE_REF));
            }
        }
    }

    private static void modifyResources(File appDir,
                                        File resDir,
                                        final VisitUtils.Visitor<TypeChunk.Entry> entryVisitor,
                                        final VisitUtils.Visitor<ResourceValue> arscResourceValueVisitor,
                                        final VisitUtils.Visitor<ResourceValue> xmlResourceValueVisitor,
                                        final VisitUtils.Visitor<XmlResourceMapChunk> resourceMapChunkVisitor) throws IOException {
        if (!appDir.exists() || appDir.isFile()) {
            throw new IllegalStateException("appDir(" + appDir.getAbsolutePath() + ") is not a dir or it not exist");
        }
        final File manifest = new File(appDir, "AndroidManifest.xml");
        final File res = resDir != null ? resDir : new File(appDir, "res");
        final File arsc = new File(appDir, "resources.arsc");
        FileUtils.iterate(appDir, new Callback<File>() {
            @Override
            public void onCallback(File file) {
                ResourceFile resourceFile = null;
                if (file.getAbsolutePath().equals(arsc.getAbsolutePath()) && (entryVisitor != null || arscResourceValueVisitor != null)) {
                    InputStream inputStream = null;
                    try {
                        inputStream = new DataInputStream(new FileInputStream(file));
                        resourceFile = ResourceFile.fromInputStream(inputStream);
                    } catch (Throwable e) {
                        throw new RuntimeException(file.getAbsolutePath(), e);
                    } finally {
                        IOUtils.closeQuietly(inputStream);
                    }
                    ResourceTableChunk resourceTableChunk = VisitUtils.getResTableFrom(resourceFile);
                    for (PackageChunk packageChunk : resourceTableChunk.getPackages()) {
                        if (entryVisitor != null) {
                            VisitUtils.visitEntry(packageChunk, entryVisitor);
                        }
                        if (arscResourceValueVisitor != null) {
                            VisitUtils.visitResourceValue(packageChunk, arscResourceValueVisitor);
                        }
                    }
                } else if (file.getName().endsWith(".xml") && (xmlResourceValueVisitor != null || resourceMapChunkVisitor != null)) {
                    if (file.getAbsolutePath().equals(manifest.getAbsolutePath())
                            || (file.getAbsolutePath().startsWith(res.getAbsolutePath()) && !"raw".equals(file.getParentFile().getName()))) {
                        InputStream inputStream = null;
                        try {
                            inputStream = new DataInputStream(new FileInputStream(file));
                            resourceFile = ResourceFile.fromInputStream(inputStream);
                        } catch (Throwable e) {
                            throw new RuntimeException(file.getAbsolutePath(), e);
                        } finally {
                            IOUtils.closeQuietly(inputStream);
                        }
                        List<Chunk> chunks = resourceFile.getChunks();
                        for (Chunk chunk : chunks) {
                            visitResourceValueInXml(chunk, xmlResourceValueVisitor, resourceMapChunkVisitor);
                        }
                    }
                }
                //update resourceFile on disk
                if (resourceFile != null) {
                    DataOutputStream outputStream = null;
                    try {
                        FileUtils.forceDelete(file);
                        outputStream = new DataOutputStream(new FileOutputStream(file));
                        outputStream.write(resourceFile.toByteArray());
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        IOUtils.closeQuietly(outputStream);
                    }
                }
            }
        }, true);
    }

    private static void visitResourceValueInXml(Chunk chunk,
                                                VisitUtils.Visitor<ResourceValue> resourceValueVisitor,
                                                VisitUtils.Visitor<XmlResourceMapChunk> resourceMapChunkVisitor) {
        if (chunk instanceof ChunkWithChunks) {
            for (Chunk child : ((ChunkWithChunks) chunk).getChunks().values()) {
                visitResourceValueInXml(child, resourceValueVisitor, resourceMapChunkVisitor);
            }
        } else if (chunk instanceof XmlStartElementChunk) {
            if (resourceValueVisitor != null) {
                List<XmlAttribute> attributes = ((XmlStartElementChunk) chunk).getAttributes();
                for (XmlAttribute attr : attributes) {
                    resourceValueVisitor.onVisit(attr.typedValue());
                }
            }
        } else if (chunk instanceof XmlResourceMapChunk) {
            if (resourceMapChunkVisitor != null) {
                resourceMapChunkVisitor.onVisit((XmlResourceMapChunk) chunk);
            }
        }
    }
}
