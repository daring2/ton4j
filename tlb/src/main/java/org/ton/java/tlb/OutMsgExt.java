package org.ton.java.tlb;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.cell.CellSlice;

/**
 *
 *
 * <pre>
 * msg_export_ext$000 msg:^(Message Any)
 * transaction:^Transaction = OutMsg;
 * </pre>
 */
@Builder
@Data
public class OutMsgExt implements OutMsg, Serializable {
  int magic;
  Message msg;
  Transaction transaction;

  @Override
  public Cell toCell() {
    return CellBuilder.beginCell()
        .storeUint(0b000, 3)
        .storeRef(msg.toCell())
        .storeRef(transaction.toCell())
        .endCell();
  }

  public static OutMsgExt deserialize(CellSlice cs) {
    return OutMsgExt.builder()
        .magic(cs.loadUint(3).intValue())
        .msg(Message.deserialize(CellSlice.beginParse(cs.loadRef())))
        .transaction(Transaction.deserialize(CellSlice.beginParse(cs.loadRef())))
        .build();
  }
}
