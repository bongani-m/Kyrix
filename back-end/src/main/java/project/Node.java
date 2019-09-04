package project;

public class Node {
    protected String id;
    protected String parent;
    protected double value;
    protected int depth;
    protected int height;
    protected int count;

    // protected ArrayList<Node> children;

    public Node() {
        this.id = "";
        this.parent = "";
        this.value = -1;
        this.depth = -1;
        this.height = -1;
        this.count = 0;
    }

    public Node(String id, String parent) {
        this.id = id;
        this.parent = parent;
    }

    public Node(Node node) {
        id = node.getId();
        parent = node.getParent();
        value = node.getValue();
        depth = node.getDepth();
        height = node.getHeight();
        count = node.getCount();
    }

    public Node(String _id, String _parent, double _value, int _depth, int _height, int _count) {
        id = _id;
        parent = _parent;
        value = _value;
        depth = _depth;
        height = _height;
        count = _count;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getParent() {
        return parent;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "Node{"
                + "id:'"
                + id
                + '\''
                + ", parent:'"
                + parent
                + '\''
                + ", value:"
                + value
                + ", height:"
                + height
                + ", depth="
                + depth
                + ", count="
                + count
                + "}";
    }
}