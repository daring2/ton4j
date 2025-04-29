package org.ton.java.tlb;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.cell.CellSlice;

/** action_reserve_currency#36e6b809 mode:(## 8) currency:CurrencyCollection = OutAction; */
@Builder
@Data
public class ActionReserveCurrency implements OutAction, Serializable {
  long magic;
  int mode;
  CurrencyCollection currency;

  @Override
  public Cell toCell() {
    return CellBuilder.beginCell()
        .storeUint(0x36e6b809, 32)
        .storeUint(mode, 8)
        .storeCell(currency.toCell())
        .endCell();
  }

  public static ActionReserveCurrency deserialize(CellSlice cs) {
    return ActionReserveCurrency.builder()
        .magic(cs.loadUint(32).intValue())
        .mode(cs.loadUint(8).intValue())
        .currency(CurrencyCollection.deserialize(cs))
        .build();
  }
}
