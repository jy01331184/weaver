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

import com.weaver.utils.FileUtils
import com.weaver.utils.ZipUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.compile.JavaCompile

import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 处理processXXXResources的输出
 *
 * 1. 解包resources.ap_，修改resources.arsc和编译后的xml文件中的id，然后重新打包resources.ap_
 * 2. 处理R.java，批量替换0x7f -> customPackageId, 替换typeId -> typeId + offset
 * 3. 处理生成的symbols文件，批量替换0x7f -> customPackageId, 替换typeId -> typeId + offset
 */
class ResPkgRemakerPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create("remaker", ResPkgRemakerExtension)
        ResPkgRemakerExtension extension = project.remaker

        project.getGradle().addBuildListener(new MergePublicListener(project))

        project.afterEvaluate {
            if (extension.enable) {
                final int customPackageId = extension.packageId
                final int typeIdOffset = extension.typeIdOffset;
                final File baseOnRes = extension.baseOnRes;
                final File[] combineReses = extension.combineRes;
                final String resDirName = extension.resDirName;

                GenerateRProcessor generateRProcessor = new GenerateRProcessor()
                ResourceChunkProcessor resourceChunkProcessor = new ResourceChunkProcessor(customPackageId, typeIdOffset, resDirName)

                project.android.applicationVariants.each { variant ->
                    String fullName = variant.getName().capitalize();

                    //hook processResources task, we will do something after it
                    Task processAndroidResourceTask = project.tasks.findByName("process${fullName}Resources")
                    Task generateSourcesTask = project.tasks.findByName("generate${fullName}Sources")
                    Task customResourceTask = project.task("custom${fullName}Resources")

                    customResourceTask.dependsOn(processAndroidResourceTask)
                    generateSourcesTask.dependsOn(customResourceTask)

                    Set<File> resourceOutputFiles = new HashSet<>();
                    JavaCompile javaCompile = variant.getJavaCompile()

                    customResourceTask.doLast {
                        TaskOutputs taskOutputs = processAndroidResourceTask.getOutputs()
                        resourceOutputFiles = taskOutputs.files.files;
                        for (File output : resourceOutputFiles) {
//                            println("==>"+output.absolutePath)
                            if (output.isDirectory() && output.absolutePath.endsWith("intermediates/res/"+variant.name)) {
                                log("find: " + output)
                                output.listFiles().each {
                                    if(it.name.endsWith(".ap_")){
                                        final File apk = it;
                                        log("processApk: " + apk.absolutePath)
                                        final File workPath = new File(apk.getParent(), "remake-res-tmp");
                                        File mergeFromWorkPath = null;
                                        try {
                                            if (baseOnRes != null) {
                                                if (!baseOnRes.exists()) {
                                                    throw new IOException("baseOnRes (${baseOnRes.absolutePath}) don't exist !")
                                                }
                                                if (baseOnRes.isFile()) {
                                                    mergeFromWorkPath = new File(baseOnRes.getParent(), "merge-from-tmp");
                                                    ZipUtils.unpack(baseOnRes, mergeFromWorkPath)
                                                } else {
                                                    mergeFromWorkPath = baseOnRes;
                                                }
                                            }
                                            FileUtils.deleteDirectory(workPath)
                                            ZipUtils.unpack(apk, workPath)
                                            //先修改packageId和typeOffset
                                            generateRProcessor.addIdMapping(resourceChunkProcessor.processChunkFiles(workPath))
                                            if (mergeFromWorkPath != null) {
                                                //然后再merge资源，应用merge资源后的idMapping和idKeepping
                                                MergeHandler.MergeResult mergeResult = MergeHandler.merge(workPath, mergeFromWorkPath);
                                                generateRProcessor.addIdMapping(mergeResult.idMapping)
                                            }
                                            apk.delete()
                                            ZipUtils.pack(workPath, apk, 9)

                                            if (combineReses != null && combineReses.size() > 0) {
                                                ZipFile[] zips = new ZipFile[combineReses.size() + 1]
                                                zips[0] = new ZipFile(apk)
                                                for (int i = 0; i < combineReses.length; i++) {
                                                    zips[i + 1] = new ZipFile(combineReses[i]);
                                                }

                                                File tmp = FileUtils.getTempFileFor(apk)
                                                MergeHandler.combine(new ZipOutputStream(tmp.newOutputStream()), new MergeHandler.IsRes<ZipFile>() {
                                                    @Override
                                                    boolean isRes(String name, ZipFile from) {
                                                        if (name == "AndroidManifest.xml" && from == zips[0]) {
                                                            return true
                                                        }
                                                        return false
                                                    }
                                                }, false, zips)
                                                FileUtils.forceDelete(apk)
                                                tmp.renameTo(apk)
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace()
                                        } finally {
                                            FileUtils.deleteQuietly(workPath)
                                            //清除掉to-merge-tmp
                                            if (mergeFromWorkPath != null && mergeFromWorkPath != baseOnRes) {
                                                FileUtils.deleteQuietly(mergeFromWorkPath)
                                            }
                                        }
                                        //clear javaCompile cache, let it recompile
                                        javaCompile = variant.getJavaCompile()
                                        for (File file : javaCompile.outputs.files.files) {
                                            FileUtils.deleteQuietly(file)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    javaCompile.doFirst {
                        for (File f : javaCompile.source.files) {
                            if (f.isFile()) {
                                if (f.name == 'R.java') {
                                    generateRProcessor.process(f)
                                }
                            } else if (f.isDirectory()) {
                                f.eachFileRecurse {
                                    file ->
                                        if (file.name == 'R.java') {
                                            generateRProcessor.process(file)
                                        }
                                }
                            }
                        }
                        for (File f : resourceOutputFiles) {
                            if (f.isFile() && f.name == 'R.txt') {
                                generateRProcessor.process(f)
                            }
                        }
                    }
                }
            }
        }
    }

    static void log(String s) {
        println("ResRemaker: " + s)
    }
}