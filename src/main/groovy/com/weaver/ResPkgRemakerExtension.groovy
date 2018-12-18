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
package com.weaver;

/**
 * author: zhoulei date: 2017/6/2.
 */
public class ResPkgRemakerExtension {
    private boolean enable = true;
    private int packageId = 0x7f;
    private int typeIdOffset = 0;
    private File baseOnRes = null;
    private File[] combineRes = null;
    private String resDirName = null;

    public int getPackageId() {
        return packageId;
    }

    public void setPackageId(int id) {
        if(id < 0x02 || id > 256) {
            throw new IllegalArgumentException("custom package id only can be > 1 and <= 255")
        }
        packageId = id;
    }

    public int setTypeIdOffset(int offset) {
        if(offset < 0 || offset >= 240) {
            //let 240 - 256 use for android
            throw new IllegalArgumentException("typeIdOffset must >= 0 and < 240");
        }
        typeIdOffset = offset;
    }

    public int getTypeIdOffset() {
        return typeIdOffset;
    }

    public void setBaseOnRes(File baseOn) {
        this.baseOnRes = baseOn;
    }

    public File getBaseOnRes() {
        return baseOnRes;
    }

    public void setCombineRes(File[] combineToRes) {
        this.combineRes = combineToRes;
    }

    public File[] getCombineRes() {
        return combineRes;
    }

    public void setEnable(boolean b) {
        enable = b;
    }

    public void setResDirName(String dirName) {
        resDirName = dirName;
    }

    public String getResDirName() {
        return resDirName;
    }

    public boolean isEnable() {
        return enable && (packageId != 0x7f || typeIdOffset > 0 || baseOnRes != null);
    }
}
