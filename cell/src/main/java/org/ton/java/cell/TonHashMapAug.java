package org.ton.java.cell;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.ton.java.bitstring.BitString;
import org.ton.java.utils.Utils;

public class TonHashMapAug  implements Serializable {

    public HashMap<Object, Pair<Object, Object>> elements; // Pair<Value,Extra>
    int keySize;
    int maxMembers;

    public TonHashMapAug(int keySize, int maxMembers) {
        elements = new LinkedHashMap<>();
        this.keySize = keySize;
        this.maxMembers = maxMembers;
    }

    public TonHashMapAug(int keySize) {
        elements = new LinkedHashMap<>();
        this.keySize = keySize;
        this.maxMembers = 10000;
    }

    public List<Node> deserializeEdge(CellSlice edge, int keySize, final BitString key) {
        if (edge.type != CellType.ORDINARY) {
            return new ArrayList<>();
        }
        List<Node> nodes = new ArrayList<>();
        BitString l = deserializeLabel(edge, keySize - key.toBitString().length());
        key.writeBitString(l);
        if (key.toBitString().length() == keySize) {
            Cell valueAndExtra = CellBuilder.beginCell().storeSlice(edge).endCell();
            nodes.add(new Node(key, valueAndExtra)); // fork-extra does not exist in edge
            return nodes;
        }

        for (int i = 0; i < edge.refs.size(); i++) {
            CellSlice forkEdge = CellSlice.beginParse(edge.refs.get(i));
            BitString forkKey = key.clone();
            forkKey.writeBit(i != 0);
            nodes.addAll(deserializeEdge(forkEdge, keySize, forkKey));
        }
        return nodes;
    }

    /**
     * Loads HashMapAug and parses keys, values and extras
     */
    void deserialize(CellSlice c,
                     Function<BitString, Object> keyParser,
                     Function<CellSlice, Object> valueParser,
                     Function<CellSlice, Object> extraParser) {
        List<Node> nodes = deserializeEdge(c, keySize, new BitString(keySize));
        for (Node node : nodes) {
            CellSlice valueAndExtra = CellSlice.beginParse(node.value);
            Object extra = extraParser.apply(valueAndExtra);
            Object value = valueParser.apply(valueAndExtra);
            elements.put(keyParser.apply(node.key), Pair.of(value, extra));
        }
    }

    /**
     * Read the keys in array and return binary tree in the form of Patrcia Tree Node
     *
     * @param nodes list which contains nodes
     * @return tree node
     */
    PatriciaTreeNode splitTree(List<Node> nodes) {
        if (nodes.size() == 1) {
            return new PatriciaTreeNode("", 0, nodes.get(0), null, null);
        }

        List<Node> left = new ArrayList<>();
        List<Node> right = new ArrayList<>();

        for (Node node : nodes) {
            boolean lr = node.key.readBit();

            if (lr) {
                right.add(node);
            } else {
                left.add(node);
            }
        }

        PatriciaTreeNode leftNode = left.size() > 1 ? splitTree(left) : left.isEmpty() ? null : new PatriciaTreeNode("", 0, left.get(0), null, null);
        PatriciaTreeNode rightNode = right.size() > 1 ? splitTree(right) : right.isEmpty() ? null : new PatriciaTreeNode("", 0, right.get(0), null, null);

        return new PatriciaTreeNode("", keySize, null, leftNode, rightNode);
    }

    /**
     * Flatten binary tree (by cutting empty branches) if possible
     *
     * @param node tree node
     * @param m    maximal possible length of prefix
     * @return flattened tree node
     */
    PatriciaTreeNode flatten(PatriciaTreeNode node, int m) {
        if (node == null) {
            return null;
        }

        if (node.maxPrefixLength == 0) {
            node.maxPrefixLength = m;
        }

        if (node.leafNode != null) {
            return node;
        }

        PatriciaTreeNode left = node.left;
        PatriciaTreeNode right = node.right;

        if (left == null) {
            return flatten(new PatriciaTreeNode(node.prefix + "1", m, null, right.left, right.right), m);
        } else if (right == null) {
            return flatten(new PatriciaTreeNode(node.prefix + "0", m, null, left.left, left.right), m);
        } else {
            node.maxPrefixLength = m;
            node.left = flatten(left, m - node.prefix.length() - 1);
            node.right = flatten(right, m - node.prefix.length() - 1);
            return node;
        }
    }

    void serialize_label(String label, int m, CellBuilder builder) {
        int n = label.length();
        if (label.isEmpty()) {
            builder.storeBit(false); //hml_short$0
            builder.storeBit(false); //Unary 0
            return;
        }

        int sizeOfM = BigInteger.valueOf(m).bitLength();
        if (n < sizeOfM) {
            builder.storeBit(false);  // hml_short
            for (int i = 0; i < n; i++) {
                builder.storeBit(true); // Unary n
            }
            builder.storeBit(false);  // Unary 0
            for (Character c : label.toCharArray()) {
                builder.storeBit(c == '1');
            }
            return;
        }

        boolean isSame = (label.equals(Utils.repeat("0", label.length())) || label.equals(Utils.repeat("10", label.length())));
        if (isSame) {
            builder.storeBit(true);
            builder.storeBit(true); //hml_same
            builder.storeBit(label.charAt(0) == '1');
            builder.storeUint(label.length(), sizeOfM);
        } else {
            builder.storeBit(true);
            builder.storeBit(false); //hml_long
            builder.storeUint(label.length(), sizeOfM);
            for (Character c : label.toCharArray()) {
                builder.storeBit(c == '1');
            }
        }
    }

    void serialize_edge(PatriciaTreeNode node, CellBuilder builder, BiFunction<Object, Object, Object> forkExtra) {
        if (node == null) {
            return;
        }
        if (node.leafNode != null) { // contains leaf
            BitString bs = node.leafNode.key.readBits(node.leafNode.key.getUsedBits());
            node.prefix = bs.toBitString();
            serialize_label(node.prefix, node.maxPrefixLength, builder);
            builder.storeCell(node.leafNode.value);
        } else { // contains fork
            serialize_label(node.prefix, node.maxPrefixLength, builder);
            CellBuilder leftCell = CellBuilder.beginCell();
            serialize_edge(node.left, leftCell, forkExtra);
            CellBuilder rightCell = CellBuilder.beginCell();
            serialize_edge(node.right, rightCell, forkExtra);
            builder.storeCell(((CellBuilder) forkExtra.apply(leftCell.endCell(), rightCell.endCell())).endCell());
            builder.storeRef(leftCell.endCell());
            builder.storeRef(rightCell.endCell());
        }
    }

    /**
     * Serializes edges and puts values into fork-nodes according to forkExtra function logic
     *
     * @param keyParser   - used on key
     * @param valueParser - used on every leaf
     * @param extraParser - used on every leaf
     * @param forkExtra   - used only in fork-node.
     * @return Cell
     */
    public Cell serialize(Function<Object, BitString> keyParser,
                          Function<Object, Object> valueParser,
                          Function<Object, Object> extraParser,
                          BiFunction<Object, Object, Object> forkExtra) {
        List<Node> nodes = new ArrayList<>();
        for (Map.Entry<Object, Pair<Object, Object>> entry : elements.entrySet()) {
            BitString key = keyParser.apply(entry.getKey());
            Cell value = (Cell) valueParser.apply(entry.getValue().getLeft());
            Cell extra = (Cell) extraParser.apply(entry.getValue().getRight());
            Cell both = CellBuilder.beginCell()
                    .storeSlice(CellSlice.beginParse(extra))
                    .storeSlice(CellSlice.beginParse(value))
                    .endCell();

            nodes.add(new Node(key, both));
        }

        if (nodes.isEmpty()) {
            throw new Error("TonHashMapAug does not support empty dict. Consider using TonHashMapAugE");
        }

        PatriciaTreeNode root = flatten(splitTree(nodes), keySize);
        CellBuilder b = CellBuilder.beginCell();
        serialize_edge(root, b, forkExtra);

        return b.endCell();
    }

    public BitString deserializeLabel(CellSlice edge, int m) {
        if (!edge.loadBit()) {
            // hml_short$0 {m:#} {n:#} len:(Unary ~n) s:(n * Bit) = HmLabel ~n m;
            return deserializeLabelShort(edge);
        }
        if (!edge.loadBit()) {
            // hml_long$10 {m:#} n:(#<= m) s:(n * Bit) = HmLabel ~n m;
            return deserializeLabelLong(edge, m);
        }
        // hml_same$11 {m:#} v:Bit n:(#<= m) = HmLabel ~n m;
        return deserializeLabelSame(edge, m);
    }

    private BitString deserializeLabelShort(CellSlice edge) {
        int length = edge.bits.getBitString().indexOf("0");
        edge.skipBits(length + 1);
        return edge.loadBits(length);
    }

    private BitString deserializeLabelLong(CellSlice edge, int m) {
        BigInteger length = edge.loadUint(BigInteger.valueOf(m).bitLength());
        return edge.loadBits(length.intValue());
    }

    private BitString deserializeLabelSame(CellSlice edge, int m) {
        boolean v = edge.loadBit();
        BigInteger length = edge.loadUint(BigInteger.valueOf(m).bitLength());
        BitString r = new BitString(length.intValue());
        for (int i = 0; i < length.intValue(); i++) {
            r.writeBit(v);
        }
        return r;
    }

    private static double log2(int n) {
        return (Math.log(n) / Math.log(2));
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (Map.Entry<Object, Pair<Object, Object>> entry : elements.entrySet()) {
            String s = String.format("[%s,(%s,%s)],", entry.getKey(), entry.getValue().getLeft(), entry.getValue().getRight());
            sb.append(s);
        }
        if (!elements.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        sb.append(")");
        return sb.toString();
    }

    public Object getKeyByIndex(long index) {
        long i = 0;
        for (Map.Entry<Object, Pair<Object, Object>> entry : elements.entrySet()) {
            if (i == index) {
                return entry.getKey();
            }
        }
        throw new Error("key not found at index " + index);
    }

    public Object getValueByIndex(long index) {
        long i = 0;
        for (Map.Entry<Object, Pair<Object, Object>> entry : elements.entrySet()) {
            if (i++ == index) {
                return entry.getValue().getLeft();
            }
        }
        throw new Error("value not found at index " + index);
    }

    public Object getEdgeByIndex(long index) {
        long i = 0;
        for (Map.Entry<Object, Pair<Object, Object>> entry : elements.entrySet()) {
            if (i++ == index) {
                return entry.getValue().getRight();
            }
        }
        throw new Error("edge not found at index " + index);
    }
}