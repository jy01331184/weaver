package com.weaver

import com.android.build.gradle.AppExtension
import com.android.utils.FileUtils
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.invocation.Gradle
/**
 * Created by xunlong.wxl on 2016/3/1.
 */
class MergePublicListener implements BuildListener {
    public Project project;

    public MergePublicListener(Project pro) {
        project = pro
    }

    @Override
    void buildStarted(Gradle gradle) {
        println("buildStarted")
    }

    @Override
    void settingsEvaluated(Settings settings) {
        println("settingsEvaluated")
    }

    @Override
    void projectsLoaded(Gradle gradle) {
        println("projectsLoaded")
    }

    @Override
    void projectsEvaluated(Gradle gradle) {
        mergePublicXml(project)
    }

    public static void mergePublicXml(Project project) {
        try {
            project.android.buildTypes.each {
                println("-----------------it = ${it.name}")
            }
            if (project.android instanceof com.android.build.gradle.LibraryExtension) {
                for (variant in ((com.android.build.gradle.LibraryExtension) project.android).libraryVariants) {
                    variant.getVariantData().getScope().getMergeResourcesTask().name
                    println variant.getVariantData().getScope().getMergeResourcesTask().name
                    println project.android.sourceSets.main.res.srcDirs
                    def scope = variant.getVariantData().getScope()
                    String mergeTaskName = scope.getMergeResourcesTask().name
                    def mergeTask = project.tasks.getByName(mergeTaskName)
                    mergeTask.doLast {
                        println("copyDependencyPublicFile")
                        List<File> publicFiles = getDependencyPublicFile(project)
                        publicFiles.addAll(getProjectPublicFile(project))
                        if (publicFiles != null) {
                            println("publicFiles size ${publicFiles.size()}")
                            for (File file : publicFiles) {
                                File targetDir = new File(mergeTask.outputDir, "values");
                                println("copy file ${file.path} to targetDir ${targetDir.path} ")
                                FileUtils.copy(file, targetDir)
                            }
                        }
                    }
                }
            } else if (project.android instanceof AppExtension) {
                for (variant in ((AppExtension) project.android).applicationVariants) {
                    variant.getVariantData().getScope().getMergeResourcesTask().name
                    println variant.getVariantData().getScope().getMergeResourcesTask().name
                    println project.android.sourceSets.main.res.srcDirs
                    def scope = variant.getVariantData().getScope()
                    String mergeTaskName = scope.getMergeResourcesTask().name
                    def mergeTask = project.tasks.getByName(mergeTaskName)
                    mergeTask.doLast {
                        println("copyDependencyPublicFile")
                        List<String> publicFiles = getDependencyPublicFile(project)
                        publicFiles.addAll(getProjectPublicFile(project))
                        if (publicFiles != null) {
                            println("publicFiles size ${publicFiles.size()}")
                            for (File file : publicFiles) {
                                File targetDir = new File(mergeTask.outputDir, "values");
                                println("copy file ${file.path} to targetDir ${targetDir.path} ")
                                FileUtils.copy(file, targetDir)
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {

        }
//        List<String> buildTypes = getBuildTypes(project)
//        for (String type : buildTypes) {
//        }
//        for (ApplicationVariantImpl variant in project.android.applicationVariants) {
//            println variant.getVariantData().getScope().getMergeResourcesTask().name
//            println project.android.sourceSets.main.res.srcDirs
//            def scope = variant.getVariantData().getScope()
//            String mergeTaskName = scope.getMergeResourcesTask().name
//            def mergeTask = project.tasks.getByName(mergeTaskName)
//            mergeTask.doLast {
//                println("copyDependencyPublicFile")
//                List<File> publicFiles = getDependencyPublicFile(project)
//                if (publicFiles != null) {
//                    println("publicFiles size ${publicFiles.size()}")
//                    for (File file : publicFiles) {
//                        File targetDir = new File(mergeTask.outputDir, "values");
//                        println("copy file ${file.path} to targetDir ${targetDir.path} ")
//                        FileUtils.copy(file, targetDir)
//                    }
//                }
//            }
//        }
    }

    public static List<String> getBuildTypes(Project project) {
        List<String> buildTypes = new ArrayList<String>();
        project.android.buildTypes.each {
            buildTypes.add(it.name)
            println("-----------------it = ${it.name}")
        }
        return buildTypes;
    }


    public static List<File> getDependencyPublicFile(Project project) {
        List<File> publicFiles = new ArrayList<File>();
        DependencySet dependencySet = project.configurations.compile.allDependencies;
        if (dependencySet == null) {
            return null
        }
        int size = dependencySet.size();
        for (int i = 0; i < size; i++) {
            if (dependencySet.getAt(i) instanceof DefaultProjectDependency) {
                Project libP = dependencySet.getAt(i).dependencyProject;
                LinkedHashSet<File> linkedHashSet = libP.android.sourceSets.main.res.srcDirs;
                int length = linkedHashSet.size();
                for (int j = 0; j < length; j++) {
                    File valueFile = new File(linkedHashSet.getAt(j), "values");
                    File publicFile = new File(valueFile, "public.xml");
                    if (publicFile.exists()) {
                        publicFiles.add(publicFile)
                    }
                }
            }
        }
        return publicFiles;
    }

    public static List<File> getProjectPublicFile(Project project) {
        List<File> publicFiles = new ArrayList<File>();
        LinkedHashSet<File> linkedHashSet = project.android.sourceSets.main.res.srcDirs;
        int length = linkedHashSet.size();
        for (int j = 0; j < length; j++) {
            File valueFile = new File(linkedHashSet.getAt(j), "values");
            File publicFile = new File(valueFile, "public.xml");
            if (publicFile.exists()) {
                publicFiles.add(publicFile)
            }
        }
        return publicFiles
    }

    @Override
    void buildFinished(BuildResult result) {

    }
}
