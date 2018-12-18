package com.weaver.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import guava.primitives.Shorts;
import pink.madis.apk.arsc.Chunk;
import pink.madis.apk.arsc.ResourceValue;
import pink.madis.apk.arsc.StringPoolChunk;
import pink.madis.apk.arsc.XmlAttribute;
import pink.madis.apk.arsc.XmlChunk;
import pink.madis.apk.arsc.XmlEndElementChunk;
import pink.madis.apk.arsc.XmlNamespaceChunk;
import pink.madis.apk.arsc.XmlNamespaceEndChunk;
import pink.madis.apk.arsc.XmlNamespaceStartChunk;
import pink.madis.apk.arsc.XmlNodeChunk;
import pink.madis.apk.arsc.XmlResourceMapChunk;
import pink.madis.apk.arsc.XmlStartElementChunk;

/**
 * 合并二进制的AndroidManifest.xml使用的工具
 * <p>
 * Created by zhoulei on 2018/2/22.
 */
public class ManifestMerger {
  public static XmlChunk mergeManifest(XmlChunk mergeTo, XmlChunk mergeFrom) {
    XmlTree mergeToTree = parseXmlTree(mergeTo);
    XmlTree mergeFromTree = parseXmlTree(mergeFrom);
    Map<AttrKey, Integer> attrResIdMap = new HashMap<>();

    //1.先收集各个attr的resId，后面编译StringPool和XmlResMap的时候会用到
    mergeToTree.collectResIds(attrResIdMap);
    mergeFromTree.collectResIds(attrResIdMap);

    //2.执行合并XmlTree的动作
    mergeXmlTree(mergeToTree, mergeFromTree);

    //3.将xmlTree压平
    return flatten(mergeToTree);
  }

  private static void mergeXmlTree(XmlTree mergeToTree, XmlTree mergeFromTree) {
    //先合并namespace
    mergeNamespaces(mergeToTree.startNameSpaces, mergeFromTree.startNameSpaces);
    mergeNamespaces(mergeToTree.endNameSpaces, mergeFromTree.endNameSpaces);
    if (mergeToTree.startNameSpaces.size() != mergeToTree.endNameSpaces.size()) {
      throw new IllegalStateException("start namespaces and end namespaces size mismatch");
    }

    //再合并<manifest>下面的第一级节点
    LinkedHashMap<String, List<XmlNode>> mergeToRootChildren = mergeToTree.getRootChildren();
    LinkedHashMap<String, List<XmlNode>> mergeFromRootChildren = mergeFromTree.getRootChildren();
    if (!mergeFromRootChildren.isEmpty()) {
      for (String key : mergeFromRootChildren.keySet()) {
        List<XmlNode> mergeToRootNodes = mergeToRootChildren.get(key);
        if (mergeToRootNodes == null) {
          mergeToRootNodes = new ArrayList<>();
          mergeToRootChildren.put(key, mergeToRootNodes);
        }
        switch (key) {
          case "instrumentation":
            mergeToRootChildren.put(key, NodeMergePolicy.CANNOT_DUPLICATE
                .merge(mergeToRootChildren.get(key),
                    mergeFromRootChildren.get(key), "name"));
            break;
          case "uses-sdk":
          case "uses-configuration":
          case "supports-screens":
          case "compatible-screens":
            //TODO 合并属性
            //TODO 合并子项
            mergeToRootChildren.put(key, NodeMergePolicy.KEEP_MERGE_TO
                .merge(mergeToRootChildren.get(key),
                    mergeFromRootChildren.get(key), null));
            break;
          case "application":
            mergeApplicationNode(mergeToRootChildren.get(key).get(0),
                mergeFromRootChildren.get(key).get(0));
            break;
          default:
            mergeToRootChildren.put(key, NodeMergePolicy.KEEP_MERGE_TO_WHEN_DUPLICATE
                .merge(mergeToRootChildren.get(key),
                    mergeFromRootChildren.get(key), "name"));
            break;
        }
      }
    }
  }

  private static void mergeNamespaces(LinkedHashMap<String, XmlNamespaceChunk> mergeTo, LinkedHashMap<String, XmlNamespaceChunk> mergeFrom) {
    for (String key : mergeFrom.keySet()) {
      if (mergeTo.containsKey(key)) {
        XmlNamespaceChunk origin = mergeTo.get(key);
        XmlNamespaceChunk from = mergeFrom.get(key);
        if (!origin.getUri().equals(from.getUri())) {
          throw new IllegalStateException(origin + " conflict with " + from);
        }
      } else {
        mergeTo.put(key, mergeFrom.get(key));
      }
    }
  }

  private static void mergeApplicationNode(XmlNode mergeTo, XmlNode mergeFrom) {
    LinkedHashMap<String, List<XmlNode>> mergeToAppChildren = mergeTo.children;
    LinkedHashMap<String, List<XmlNode>> mergeFromAppChildren = mergeFrom.children;
    if (!mergeFromAppChildren.isEmpty()) {
      for (String key : mergeFromAppChildren.keySet()) {
        List<XmlNode> mergeToAppNodes = mergeToAppChildren.get(key);
        if (mergeToAppNodes == null) {
          mergeToAppNodes = new ArrayList<>();
          mergeToAppChildren.put(key, mergeToAppNodes);
        }
        switch (key) {
          case "activity":
          case "activity-alias":
          case "service":
          case "receiver":
          case "provider":
          case "uses-library":
          case "meta-data":
            mergeToAppChildren.put(key, NodeMergePolicy.KEEP_MERGE_TO_WHEN_DUPLICATE
                .merge(mergeToAppChildren.get(key),
                    mergeFromAppChildren.get(key), "name"));
            break;
          default:
            mergeToAppChildren.put(key, NodeMergePolicy.KEEP_MERGE_TO_WHEN_DUPLICATE
                .merge(mergeToAppChildren.get(key),
                    mergeFromAppChildren.get(key), "name"));
            break;
        }
      }
    }
  }

  private static XmlTree parseXmlTree(XmlChunk xmlChunk) {
    LinkedHashMap<String, XmlNamespaceChunk> startNamespaces = new LinkedHashMap<>();
    LinkedHashMap<String, XmlNamespaceChunk> endNamespaces = new LinkedHashMap<>();
    for (Chunk c : xmlChunk.getChunks().values()) {
      if (c instanceof XmlNamespaceStartChunk) {
        XmlNamespaceStartChunk namespaceChunk = (XmlNamespaceStartChunk) c;
        startNamespaces.put(namespaceChunk.getPrefix(), namespaceChunk);
      } else if (c instanceof XmlNamespaceEndChunk) {
        XmlNamespaceEndChunk namespaceChunk = (XmlNamespaceEndChunk) c;
        endNamespaces.put(namespaceChunk.getPrefix(), namespaceChunk);
      }
    }

    Stack<XmlNode> xmlNodeStack = new Stack<>();
    XmlNode root = null;
    XmlNode current = null;
    XmlNode parent = null;
    for (Chunk c : xmlChunk.getChunks().values()) {
      if (c instanceof XmlStartElementChunk) {
        XmlStartElementChunk startElementChunk = (XmlStartElementChunk) c;
        current = new XmlNode(startElementChunk);
        if (root == null) {
          root = current;
        } else {
          parent = xmlNodeStack.peek();
          List<XmlNode> xmlNodes = parent.children.get(current.name);
          if (xmlNodes == null) {
            xmlNodes = new ArrayList<>();
            parent.children.put(current.name, xmlNodes);
          }
          xmlNodes.add(current);
        }
        xmlNodeStack.push(current);
      } else if (c instanceof XmlEndElementChunk) {
        XmlNode xmlNode = xmlNodeStack.pop();
        xmlNode.setEndElementChunk((XmlEndElementChunk) c);
      }
    }

    StringPoolChunk stringPoolChunk = null;
    XmlResourceMapChunk resourceMapChunk = null;
    for (Chunk c : xmlChunk.getChunks().values()) {
      if (c instanceof StringPoolChunk) {
        //xml的stringPool
        stringPoolChunk = (StringPoolChunk) c;
      } else if (c instanceof XmlResourceMapChunk) {
        //attr在xml stringPool中的位置，所对应的attr的resId
        resourceMapChunk = (XmlResourceMapChunk) c;
      }
      if (stringPoolChunk != null && resourceMapChunk != null) {
        break;
      }
    }

    return new XmlTree(startNamespaces, endNamespaces, stringPoolChunk, resourceMapChunk, root);
  }

  private static XmlChunk flatten(XmlTree xmlTree) {
    //1.首先收集xml中的具有resId的attrName的String
    List<String> stringPool = new ArrayList<>();
    List<Integer> resIds = new ArrayList<>();
    xmlTree.root.collectResIdStrings(stringPool, resIds);

    //2.然后将xml中没有resId的string收集起来
    LinkedHashSet<String> uniqueStringPool = new LinkedHashSet<>();
    for (XmlNamespaceChunk nameSpace : xmlTree.startNameSpaces.values()) {
      uniqueStringPool.add(nameSpace.getPrefix());
      uniqueStringPool.add(nameSpace.getUri());

      XmlNamespaceChunk endNameSpace = xmlTree.endNameSpaces.get(nameSpace.getPrefix());

      int prefixIndex = stringPool.size() + uniqueStringPool.size() - 2;
      nameSpace.setPrefixIndex(prefixIndex);
      endNameSpace.setPrefixIndex(prefixIndex);

      int uriIndex = stringPool.size() + uniqueStringPool.size() - 1;
      nameSpace.setUriIndex(uriIndex);
      endNameSpace.setUriIndex(uriIndex);
    }
    xmlTree.root.collectOtherStrings(uniqueStringPool);

    uniqueStringPool.remove(null);

    //3. 将1，2组成最终的StringPool
    stringPool.addAll(uniqueStringPool);
    StringPoolChunk stringPoolChunk = xmlTree.stringPoolChunk;
    stringPoolChunk.strings.clear();
    stringPoolChunk.strings.addAll(stringPool);
    stringPoolChunk.styles.clear();

    //4. attrName的resId位置和StringPool中的位置要对应好
    XmlResourceMapChunk xmlResourceMapChunk = xmlTree.resourceMapChunk;
    xmlResourceMapChunk.resources.clear();
    xmlResourceMapChunk.resources.addAll(resIds);

    //5. 再计算XmlNode的堆栈
    List<XmlNodeChunk> xmlNodeList = new LinkedList<>();
    xmlTree.root.collectNodeChunk(xmlNodeList);

    //6. 创建XmlChunk
    ByteBuffer byteBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    byteBuffer.putShort(Shorts.checkedCast(0x0003));
    byteBuffer.putShort((short) 8);
    byteBuffer.putInt(8);
    byteBuffer.rewind();
    XmlChunk xmlChunk = (XmlChunk) Chunk.newInstance(byteBuffer);

    //7. 然后重新调整StringPool位置
    xmlTree.root.adjustStringIndex(xmlChunk, stringPoolChunk, resIds);

    //8. 写stringPool和resourceMap
    xmlChunk.appendChunk(stringPoolChunk);
    xmlChunk.appendChunk(xmlResourceMapChunk);

    //9. 开始写XmlChunk，先标示nameSpace开始
    for (XmlNamespaceChunk xmlNamespaceChunk : xmlTree.startNameSpaces.values()) {
      xmlNamespaceChunk.setParent(xmlChunk);
      xmlChunk.appendChunk(xmlNamespaceChunk);
    }

    //10. 写XmlNode
    int depth = 0;
    for (XmlNodeChunk xmlNodeChunk : xmlNodeList) {
      if (xmlNodeChunk instanceof XmlStartElementChunk) {
        depth++;
      } else {
        depth--;
      }
      if (depth < 0) {
        throw new IllegalStateException("XmlNode depth incorrect! " + xmlNodeChunk);
      }
      xmlChunk.appendChunk(xmlNodeChunk);
    }

    //11. 标示nameSpace结束
    for (XmlNamespaceChunk xmlNamespaceChunk : xmlTree.endNameSpaces.values()) {
      xmlNamespaceChunk.setParent(xmlChunk);
      xmlChunk.appendChunk(xmlNamespaceChunk);
    }

    return xmlChunk;
  }

  private static class XmlTree {
    private LinkedHashMap<String, XmlNamespaceChunk> startNameSpaces;
    private LinkedHashMap<String, XmlNamespaceChunk> endNameSpaces;
    private StringPoolChunk stringPoolChunk;
    private XmlResourceMapChunk resourceMapChunk;
    private XmlNode root;

    private XmlTree(LinkedHashMap<String, XmlNamespaceChunk> startNameSpaces,
                    LinkedHashMap<String, XmlNamespaceChunk> endNameSpaces,
                    StringPoolChunk stringPoolChunk,
                    XmlResourceMapChunk resourceMapChunk, XmlNode root) {
      this.startNameSpaces = startNameSpaces;
      this.endNameSpaces = endNameSpaces;
      this.root = root;
      this.stringPoolChunk = stringPoolChunk;
      this.resourceMapChunk = resourceMapChunk;
    }

    public LinkedHashMap<String, List<XmlNode>> getRootChildren() {
      return root.children;
    }

    private void collectResIds(Map<AttrKey, Integer> attrResIdMap) {
      if (root != null) {
        root.collectResIds(attrResIdMap, resourceMapChunk);
      }
    }
  }

  private static class XmlNode {
    private final String name;
    private final int line;
    private final String comment;
    private final String namespace;
    private final LinkedHashMap<String, XmlAttributeWrapper> attrs = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<XmlNode>> children = new LinkedHashMap<>();

    private final XmlStartElementChunk startElementChunk;
    private XmlEndElementChunk endElementChunk;

    private XmlNode(XmlStartElementChunk startElementChunk) {
      name = startElementChunk.getName();
      line = startElementChunk.getLineNumber();
      comment = startElementChunk.getComment();
      namespace = startElementChunk.getNamespace();
      for (XmlAttribute attribute : startElementChunk.getAttributes()) {
        attrs.put(attribute.name(), new XmlAttributeWrapper(attribute, -1));
      }
      this.startElementChunk = startElementChunk;
    }

    private void collectResIds(Map<AttrKey, Integer> attrResIdMap, XmlResourceMapChunk resourceMapChunk) {
      for (XmlAttributeWrapper attribute : attrs.values()) {
        int nameIndex = attribute.xmlAttribute.nameIndex();
        int resId = -1;
        if (nameIndex < resourceMapChunk.resources.size()) {
          resId = resourceMapChunk.resources.get(nameIndex);
        }
        AttrKey attrKey = new AttrKey(attribute.name(), attribute.nameSpace());
        attribute.resId = resId;
        Integer origin = attrResIdMap.put(attrKey, resId);
        if (origin != null && origin != resId) {
          throw new IllegalStateException("duplicate attr found!, attr=" + attrKey);
        }
      }

      for (List<XmlNode> childList : children.values()) {
        for (XmlNode child : childList) {
          child.collectResIds(attrResIdMap, resourceMapChunk);
        }
      }
    }

    private void collectResIdStrings(List<String> stringsPool, List<Integer> resIds) {
      for (XmlAttributeWrapper attribute : attrs.values()) {
        if (attribute.resId != -1) {
          int index = resIds.indexOf(attribute.resId);
          if (index < 0) {
            stringsPool.add(attribute.name());
            resIds.add(attribute.resId);
          } else {
            String origin = stringsPool.get(index);
            if (!origin.equals(attribute.name)) {
              throw new IllegalStateException(attribute.xmlAttribute + " has duplicate res id");
            }
          }
        }
      }
      for (List<XmlNode> childList : children.values()) {
        for (XmlNode child : childList) {
          child.collectResIdStrings(stringsPool, resIds);
        }
      }
    }

    private void collectOtherStrings(LinkedHashSet<String> stringsPool) {
      stringsPool.add(name);
      stringsPool.add(namespace);
      stringsPool.add(comment);
      for (XmlAttributeWrapper attribute : attrs.values()) {
        if (attribute.resId == -1) {
          stringsPool.add(attribute.name());
        }
        stringsPool.add(attribute.nameSpace());
        stringsPool.add(attribute.rawValue());
        stringsPool.add(attribute.typedString);
      }
      for (List<XmlNode> childList : children.values()) {
        for (XmlNode child : childList) {
          child.collectOtherStrings(stringsPool);
        }
      }
    }

    private void setEndElementChunk(XmlEndElementChunk endElementChunk) {
      this.endElementChunk = endElementChunk;
    }

    private void adjustStringIndex(Chunk parent, StringPoolChunk stringPool, List<Integer> resIdMap) {
      startElementChunk.setParent(parent);
      endElementChunk.setParent(parent);

      int nameIndex = stringPool.indexOf(name);
      int nameSpaceIndex = stringPool.indexOf(namespace);
      int commentIndex = stringPool.indexOf(comment);
      startElementChunk.setNameIndex(nameIndex);
      startElementChunk.setNamespaceIndex(nameSpaceIndex);
      startElementChunk.setCommentIndex(commentIndex);
      endElementChunk.setNameIndex(nameIndex);
      endElementChunk.setNamespaceIndex(nameSpaceIndex);
      endElementChunk.setCommentIndex(commentIndex);

      for (XmlAttributeWrapper attribute : attrs.values()) {
        int attrNameIndex = -1;
        int attrNameSpaceIndex = -1;
        int attrRawValueIndex = -1;
        if (attribute.resId != -1) {
          attrNameIndex = resIdMap.indexOf(attribute.resId);
          if (!stringPool.getString(attrNameIndex).equals(attribute.name)) {
            throw new IllegalStateException("can't find resId for " + attribute.xmlAttribute);
          }
        } else {
          attrNameIndex = stringPool.indexOf(attribute.name);
        }
        attrNameSpaceIndex = stringPool.indexOf(attribute.nameSpace);
        attrRawValueIndex = stringPool.indexOf(attribute.rawValue);
        attribute.xmlAttribute.setNameIndex(attrNameIndex);
        attribute.xmlAttribute.setNamespaceIndex(attrNameSpaceIndex);
        attribute.xmlAttribute.setRawValueIndex(attrRawValueIndex);
        if (attribute.typedString != null) {
          int attrTypedStringIndex = stringPool.indexOf(attribute.typedString);
          attribute.xmlAttribute.typedValue().setData(attrTypedStringIndex);
        }
      }

      for (List<XmlNode> childList : children.values()) {
        for (XmlNode child : childList) {
          child.adjustStringIndex(parent, stringPool, resIdMap);
        }
      }
    }

    private void collectNodeChunk(List<XmlNodeChunk> xmlNodeList) {
      xmlNodeList.add(startElementChunk);

      for (List<XmlNode> childList : children.values()) {
        for (XmlNode child : childList) {
          child.collectNodeChunk(xmlNodeList);
        }
      }

      xmlNodeList.add(endElementChunk);
    }

    @Override
    public String toString() {
      return "XmlNode{name=" + name + " nameSpace=" + namespace + "}";
    }
  }

  private static class XmlAttributeWrapper {
    private final XmlAttribute xmlAttribute;
    private final String name;
    private final String nameSpace;
    private final String rawValue;
    private final String typedString;
    private int resId;

    private XmlAttributeWrapper(XmlAttribute xmlAttribute, int resId) {
      this.xmlAttribute = xmlAttribute;
      this.resId = resId;
      this.name = xmlAttribute.name();
      this.nameSpace = xmlAttribute.namespace();
      this.rawValue = xmlAttribute.rawValue();
      ResourceValue resourceValue = xmlAttribute.typedValue();
      if (resourceValue != null && resourceValue.type().code() == ResourceValue.Type.STRING.code()) {
        typedString = xmlAttribute.getString(resourceValue.data());
      } else {
        typedString = null;
      }
    }

    String name() {
      return name;
    }

    String nameSpace() {
      return nameSpace;
    }

    String rawValue() {
      return rawValue;
    }
  }

  private static class XmlAttributeKey {
    private XmlAttributeWrapper xmlAttributeWrapper;

    private XmlAttributeKey(XmlAttributeWrapper xmlAttributeWrapper) {
      this.xmlAttributeWrapper = xmlAttributeWrapper;
    }

    public static XmlAttributeKey wrap(XmlAttributeWrapper xmlAttributeWrapper) {
      return new XmlAttributeKey(xmlAttributeWrapper);
    }

    @Override
    public boolean equals(Object object) {
      if (super.equals(object)) {
        return true;
      } else if (object instanceof XmlAttributeKey) {
        XmlAttributeKey other = (XmlAttributeKey) object;
        if (other.xmlAttributeWrapper.equals(xmlAttributeWrapper)) {
          return true;
        }
        if (xmlAttributeWrapper != null) {
          return other.xmlAttributeWrapper.name.equals(xmlAttributeWrapper.name)
              && other.xmlAttributeWrapper.nameSpace.equals(xmlAttributeWrapper.nameSpace)
              && other.xmlAttributeWrapper.rawValue.equals(xmlAttributeWrapper.rawValue);
        }
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      if (xmlAttributeWrapper != null) {
        h *= 1000003;
        h ^= strHashCode(xmlAttributeWrapper.name);
        h *= 1000003;
        h ^= strHashCode(xmlAttributeWrapper.nameSpace);
        h *= 1000003;
        h ^= strHashCode(xmlAttributeWrapper.rawValue);
      }
      return h;
    }
  }

  private static class AttrKey {
    private String name;
    private String nameSpace;

    private AttrKey(String name, String nameSpace) {
      this.name = name;
      this.nameSpace = nameSpace;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      } else if (object instanceof AttrKey) {
        return toString().equals(object.toString());
      }
      return false;
    }

    @Override
    public String toString() {
      return "AttrKey{name=" + name + " nameSpace=" + nameSpace + "}";
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= strHashCode(name);
      h *= 1000003;
      h ^= strHashCode(nameSpace);
      return h;
    }
  }

  private static int strHashCode(String s) {
    if (s == null) {
      return "__null".hashCode();
    } else {
      return s.hashCode();
    }
  }

  private enum NodeMergePolicy {
    NODE_KEEP_ALL,
    KEEP_MERGE_TO,
    KEEP_MERGE_TO_WHEN_DUPLICATE,
    CANNOT_DUPLICATE;

    /**
     * merge同一层级下，同一类型的节点
     *
     * @param mergeTo   宿主节点
     * @param mergeFrom 要merge的节点
     * @param keyName   唯一区分节点的attr名
     * @return 合并后的节点
     */
    public List<XmlNode> merge(List<XmlNode> mergeTo, List<XmlNode> mergeFrom, String keyName) {
      List<XmlNode> mergedResult = new ArrayList<>();
      if (mergeTo == null) {
        mergeTo = Collections.emptyList();
      }
      if (mergeFrom == null) {
        mergeFrom = Collections.emptyList();
      }
      LinkedHashMap<XmlAttributeKey, XmlNode> mergedMap;

      switch (NodeMergePolicy.this) {
        case NODE_KEEP_ALL:
          mergedResult.addAll(mergeTo);
          mergedResult.addAll(mergeFrom);
          break;
        case KEEP_MERGE_TO:
          mergedResult.addAll(mergeTo);
          if (mergedResult.isEmpty()) {
            mergedResult.addAll(mergeFrom);
          }
          break;
        case KEEP_MERGE_TO_WHEN_DUPLICATE:
          if (keyName == null) {
            throw new IllegalArgumentException("keyName can't be null when KEEP_MERGE_TO_WHEN_DUPLICATE");
          }
          mergedMap = new LinkedHashMap<>();
          for (XmlNode node : mergeFrom) {
            XmlAttributeKey key = XmlAttributeKey.wrap(node.attrs.get(keyName));
            mergedMap.put(key, node);
          }
          for (XmlNode node : mergeTo) {
            XmlAttributeKey key = XmlAttributeKey.wrap(node.attrs.get(keyName));
            mergedMap.put(key, node);
          }
          mergedResult.addAll(mergedMap.values());
          break;
        case CANNOT_DUPLICATE:
          if (keyName == null) {
            throw new IllegalArgumentException("keyName can't be null when KEEP_MERGE_TO_WHEN_DUPLICATE");
          }
          mergedMap = new LinkedHashMap<>();
          for (XmlNode node : mergeFrom) {
            XmlAttributeKey key = XmlAttributeKey.wrap(node.attrs.get(keyName));
            mergedMap.put(key, node);
          }
          for (XmlNode node : mergeTo) {
            XmlAttributeKey key = XmlAttributeKey.wrap(node.attrs.get(keyName));
            if (mergedMap.put(key, node) != null) {
              throw new IllegalStateException("xml attr complicate!, attr:" + (key.xmlAttributeWrapper == null ? null : key.xmlAttributeWrapper.xmlAttribute) + " node:" + node);
            }
          }
          mergedResult.addAll(mergedMap.values());
          break;
      }
      return mergedResult;
    }
  }
}
