/*
 * Copyright (C) 2017 seiginonakama (https://github.com/seiginonakama).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.weaver

import com.android.annotations.Nullable
import com.weaver.utils.FileUtils
import com.weaver.utils.IOUtils
import com.weaver.utils.ResIdUtils
import com.weaver.utils.ResUtils
import com.weaver.utils.VisitUtils
import groovy.io.FileType
import pink.madis.apk.arsc.*

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 1. modify app PackageChunk id, add typeIdOffset
 * 2. modify all 0x[0-7][2-9a-fA-F]  res id in app PackageChunk，.xml
 * 3. add LibraryChunk(DynamicRefTable) for PackageChunk, because android 5.0+ don't recognize customPackageId
 *
 * author: zhoulei date: 2017/6/2.
 */
public class ResourceChunkProcessor {
    protected final int customPackageId;
    protected final int typeIdOffset;
    protected final String resDirName;

    private static final String ARSC_FILE = "resources.arsc"
    private static final String RES_DIR = "res"

    public ResourceChunkProcessor(int customPackageId, int typeIdOffset) {
        this(customPackageId, typeIdOffset, null);
    }

    public ResourceChunkProcessor(int customPackageId, int typeIdOffset, String resDirName) {
        this.customPackageId = customPackageId;
        this.typeIdOffset = typeIdOffset;
        this.resDirName = resDirName;
    }

    /**
     * 处理customPackageId和typeIdOffset
     *
     * @param chunkRoot aapt生成物解压后的根目录
     */
    @Nullable
    public Map<Integer, Integer> processChunkFiles(File chunkRoot) {
        File arsc = new File(chunkRoot, ARSC_FILE);
        if (arsc.isFile()) {
            //如果指定了resDirName，就准备好修改StringPool里面资源文件的位置
            Map<String, String> resPathMap = null;
            File resDir = new File(chunkRoot, RES_DIR)
            boolean resDirChanged = resDirName != null && resDirName != RES_DIR;
            if (resDirChanged) {
                resPathMap = new HashMap<>();
                if (resDir.exists()) {
                    resDir.eachFileRecurse(FileType.FILES, {
                        file ->
                            String originPath = file.getAbsolutePath().substring(chunkRoot.absolutePath.length() + 1)
                            String mappedPath = resDirName + originPath.substring(RES_DIR.length())
                            resPathMap.put(originPath, mappedPath)
                    })
                }
            }

            Map<Integer, Integer> idMapping = processResourceTable(arsc, resPathMap);

            if (idMapping != null && !idMapping.isEmpty()) {
                ResIdUtils.modifyRefResourceId(chunkRoot, new ResIdUtils.Modifier() {
                    @Override
                    int onModify(int originResId, int type) {
                        Integer remakedResId = idMapping.get(originResId)
                        if (remakedResId != null) {
                            return remakedResId
                        }
                        return originResId;
                    }
                })
            }

            //之后再更改res目录名字
            if (resDirChanged && resDir.exists()) {
                FileUtils.moveDirectory(resDir, new File(chunkRoot, resDirName))
            }
            return idMapping
        }
        return null
    }

    public Map<Integer, Integer> processResourceTable(File arsc, Map<String, String> resPathMap) {
        Map<Integer, Integer> idMapping = new HashMap<>()

        InputStream inputStream = arsc.newDataInputStream();
        ResourceFile resourceFile = ResourceFile.fromInputStream(inputStream)
        IOUtils.closeQuietly(inputStream)

        ResourceTableChunk tableChunk = VisitUtils.getResTableFrom(resourceFile)
        Map<Integer, Chunk> chunkMap = tableChunk.chunks
        for (Map.Entry<Integer, Chunk> entry : chunkMap) {
            Chunk c = entry.getValue();

            //packageId大于0x02的认定为AppPackage
            if (c instanceof PackageChunk && c.id >= 0x02 && (c.id != customPackageId || typeIdOffset >= 0)) {
                println("start process PackageChunk, id:${c.id} packageName:${c.packageName}")
                PackageChunk oldPackageChunk = c;

                //只处理App PackageChunk
                byte[] libraryChunkBytes = createLibraryChunk(oldPackageChunk.packageName).toByteArray()
                byte[] packageChunkBytes = oldPackageChunk.toByteArray();
                byte[] newPackageChunkBytes = new byte[packageChunkBytes.length + libraryChunkBytes.length]
                //type + header + payload
                System.arraycopy(packageChunkBytes, 0, newPackageChunkBytes, 0, packageChunkBytes.size())

                ByteBuffer buffer = ByteBuffer.wrap(newPackageChunkBytes).order(ByteOrder.LITTLE_ENDIAN);
                buffer.getShort() //skip type
                buffer.getShort() //skip header size
                buffer.putInt(newPackageChunkBytes.length) //rewrite chunk size
                buffer.putInt(customPackageId) //rewrite package id
                buffer.position(packageChunkBytes.length)
                buffer.put(libraryChunkBytes) //write library chunk
                buffer.rewind()

                buffer.getShort() //skip type
                PackageChunk newPackageChunk = new PackageChunk(buffer, null)
                newPackageChunk.init(buffer)
                if (typeIdOffset > 0) {
                    modifyTypeChunkId(newPackageChunk)
                }
                VisitUtils.visitEntry(oldPackageChunk, new VisitUtils.Visitor<TypeChunk.Entry>() {
                    @Override
                    void onVisit(TypeChunk.Entry e) {
                        int originResId = ResIdUtils.makeResId(oldPackageChunk.id, e.parent().id, e.index())
                        int remakedResId = ResIdUtils.makeResId(newPackageChunk.id, e.parent().id + typeIdOffset, e.index())
                        idMapping.put(originResId, remakedResId)
                    }
                })
                entry.setValue(newPackageChunk)
            }
        }

        if (resPathMap != null) {
            ResUtils.modifyResPath(tableChunk, resPathMap)
        }

        arsc.delete()
        DataOutputStream output = arsc.newDataOutputStream();
        output.write(resourceFile.toByteArray())
        output.flush()
        output.close()

        return idMapping
    }

    public static void modifyResPath(ResourceTableChunk tableChunk, Map<String, String> resPathMap) {
        StringPoolChunk stringPoolChunk = tableChunk.getStringPool()
        List<String> strings = stringPoolChunk.strings
        Map<Integer, String> modifiedStringMap = new HashMap<>()
        for(int i = 0; i < strings.size(); i++) {
            String s = strings.get(i)
            String mapped = resPathMap.get(s)
            if (mapped != null) {
                modifiedStringMap.put(i, mapped)
            }
        }
        for(Map.Entry<Integer, String> entry : modifiedStringMap) {
            strings.set(entry.key, entry.value)
        }
    }

    private void modifyTypeChunkId(PackageChunk packageChunk) {
        StringPoolChunk typeStringPool = packageChunk.getTypeStringPool()
        List<String> strings = typeStringPool.strings
        List<StringPoolChunk.StringPoolStyle> styles = typeStringPool.styles
        for (int i = 0; i < typeIdOffset; i++) {
            //index of type strings <-> typeId, so add offset to strings
            strings.add(i, '<empty>')
            styles.add(i, new StringPoolStyleImpl(Collections.emptyList()))
        }
        for (Collection<TypeChunk> typeChunks : packageChunk.typeChunks.values()) {
            for (TypeChunk typeChunk : typeChunks) {
                typeChunk.id += typeIdOffset;
            }
        }
        for (TypeSpecChunk typeSpecChunk : packageChunk.typeSpecChunks.values()) {
            typeSpecChunk.id += typeIdOffset;
        }
    }

    private LibraryChunk createLibraryChunk(String packageName) {
        final short headerSize = 12 //type(2) + headerSize(2) + chunkSize(4) + entrySize(4)
        final int libChunkSize = headerSize + LibraryChunk.Entry.SIZE; //headerSize + 1 * Entry.SIZE
        ByteBuffer libByteBuffer = ByteBuffer.allocate(libChunkSize);
        libByteBuffer.putShort(Chunk.Type.TABLE_LIBRARY.code()) //type
        libByteBuffer.putShort(headerSize) //headerSize
        libByteBuffer.putInt(libChunkSize) //chunkSize
        libByteBuffer.putInt(1) //entrySize
        libByteBuffer.putInt(customPackageId) //packageId
        PackageUtils.writePackageName(libByteBuffer, packageName)
        libByteBuffer.rewind()

        return Chunk.newInstance(libByteBuffer)
    }
}
