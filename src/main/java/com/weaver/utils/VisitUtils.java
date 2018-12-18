package com.weaver.utils;

import pink.madis.apk.arsc.*;
/**
 * 资源数据轮询修改工具
 *
 * Created by zhoulei on 17/7/21.
 */
public class VisitUtils {
    public interface Visitor<T> {
        void onVisit(T t);
    }

    public static void visitResourceValue(PackageChunk packageChunk, final Visitor<ResourceValue> visitor) {
        visitEntry(packageChunk, new Visitor<TypeChunk.Entry>() {
            @Override
            public void onVisit(TypeChunk.Entry entry) {
                if (entry.isComplex()) {
                    for (ResourceValue value : entry.values().values()) {
                        visitor.onVisit(value);
                    }
                } else {
                    visitor.onVisit(entry.value());
                }
            }
        });
    }

    public static void visitEntry(PackageChunk packageChunk, Visitor<TypeChunk.Entry> visitor) {
        for (Chunk chunk : packageChunk.chunks.values()) {
            if (chunk instanceof TypeChunk) {
                for (TypeChunk.Entry entry : ((TypeChunk) chunk).getEntries().values()) {
                    if (entry != null) {
                        visitor.onVisit(entry);
                    }
                }
            }
        }
    }

    public static ResourceTableChunk getResTableFrom(ResourceFile resourceFile) {
        for (Chunk chunk : resourceFile.getChunks()) {
            if (chunk instanceof ResourceTableChunk) {
                return (ResourceTableChunk) chunk;
            }
        }
        return null;
    }
}
