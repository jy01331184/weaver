
package pink.madis.apk.arsc;

public final class ResourceValueImpl extends ResourceValue {

  private final int size;
  private final Type type;
  private int data;

  ResourceValueImpl(
      int size,
      Type type,
      int data) {
    this.size = size;
    if (type == null) {
      throw new NullPointerException("Null type");
    }
    this.type = type;
    this.data = data;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Type type() {
    return type;
  }

  @Override
  public int data() {
    return data;
  }

   @Override
   public void setData(int data) {
     this.data = data;
   }

   @Override
  public String toString() {
    return "ResourceValue{"
        + "size=" + size + ", "
        + "type=" + type + ", "
        + "data=" + data
        + "}";
  }

//  @Override
//  public boolean equals(Object o) {
//    if (o == this) {
//      return true;
//    }
//    if (o instanceof ResourceValue) {
//      ResourceValue that = (ResourceValue) o;
//      return (this.size == that.size())
//           && (this.type.equals(that.type()))
//           && (this.data == that.data());
//    }
//    return false;
//  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.size;
    h *= 1000003;
    h ^= this.type.hashCode();
    h *= 1000003;
    h ^= this.data;
    return h;
  }

}
