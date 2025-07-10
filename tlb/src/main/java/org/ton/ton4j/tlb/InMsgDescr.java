package org.ton.ton4j.tlb;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellBuilder;
import org.ton.ton4j.cell.CellSlice;
import org.ton.ton4j.cell.TonHashMapAugE;

/**
 *
 *
 * <pre>
 * (HashmapAugE 256 InMsg ImportFees) = InMsgDescr;
 * </pre>
 */
@Builder
@Data
public class InMsgDescr implements Serializable {
  TonHashMapAugE inMsg;

  public Cell toCell() {
    return CellBuilder.beginCell()
        .storeDict(
            inMsg.serialize(
                k -> CellBuilder.beginCell().storeUint((BigInteger) k, 256).endCell().getBits(),
                v -> CellBuilder.beginCell().storeCell(((InMsg) v).toCell()),
                e -> CellBuilder.beginCell().storeCell(((ImportFees) e).toCell()),
                (fk, fv) -> CellBuilder.beginCell().storeUint(0, 1) // todo
                ))
        .endCell();
  }

  public static InMsgDescr deserialize(CellSlice cs) {
    return InMsgDescr.builder()
        .inMsg(
            cs.loadDictAugE(256, k -> k.readUint(256), InMsg::deserialize, ImportFees::deserialize))
        .build();
  }

  public long getCount() {
    return inMsg.elements.size();
  }

  public List<InMsg> getInMessages() {
    List<InMsg> inMsgs = new ArrayList<>();
    for (Map.Entry<Object, Pair<Object, Object>> entry : inMsg.elements.entrySet()) {
      inMsgs.add((InMsg) entry.getValue().getLeft());
    }
    return inMsgs;
  }
}
