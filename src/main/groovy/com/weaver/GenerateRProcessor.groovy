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

import java.util.regex.Matcher
import java.util.regex.Pattern
/**
 * apply idMapping to R.java and R.txt
 *
 * author: zhoulei date: 2017/6/2.
 */
public class GenerateRProcessor {
    private List<Map<Integer, Integer>> idMappingChain;
    private static final Pattern RID_APP = Pattern.compile("0x(0[2-9a-fA-F]|[1-7][0-9a-fA-F])[0-9a-fA-F]{6}")

    public void process(File file) {
        if (file.name == "R.java") {
            println("processR: " + file.absolutePath)
        } else {
            println("processSymbol: " + file.absolutePath)
        }
//        if (idMappingChain != null) {
//            for (int i = 0; i < idMappingChain.size(); i++) {
//                Map<Integer, Integer> idMapping = idMappingChain.get(i);
//                for (Map.Entry<Integer, Integer> map : idMapping) {
//                    Logger.log("chain(${i}) idMapping: ${toIdString(map.key)} -> ${toIdString(map.value)}")
//                }
//            }
//        }
        File newFile = new File(file.absolutePath + '.tmp')
        BufferedWriter writer = newFile.newWriter(false);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            Matcher matcher = RID_APP.matcher(line)
            while (matcher.find()) {
                String match = matcher.group();
                int resId = Integer.parseInt(match.substring(2, match.length()), 16)
                int mapping = resId;
                if (idMappingChain != null) {
                    mapping = getMappedId(idMappingChain, resId)
                }
                if (mapping != resId) {
                    resId = mapping
                    line = line.replaceAll(match, toIdString(resId))
                }
            }
            writer.writeLine(line)
        }
        bufferedReader.close()
        file.delete()
        writer.flush()
        writer.close()
        newFile.renameTo(file)
    }

    void addIdMapping(Map<Integer, Integer> idMapping) {
        if (idMapping != null) {
            if (idMappingChain == null) {
                idMappingChain = new ArrayList<>();
            }
            Map<Integer, Integer> map = new HashMap<>()
            map.putAll(idMapping)
            idMappingChain.add(map)
        }
    }

    static int getMappedId(List<Map<Integer, Integer>> idMappingChain, int resId) {
        if (idMappingChain == null || idMappingChain.isEmpty()) {
            return resId;
        }
        int mapped = resId;
        for (Map<Integer, Integer> idMapping : idMappingChain) {
            Integer tmp = idMapping.get(mapped);
            if (tmp == null) {
                return mapped;
            } else {
                mapped = tmp;
            }
        }
        return mapped;
    }

    static String toIdString(int resId) {
        String hex = Integer.toHexString(resId);
        while (hex.length() < 8) {
            hex = "0" + hex;
        }
        return "0x" + hex;
    }
}
