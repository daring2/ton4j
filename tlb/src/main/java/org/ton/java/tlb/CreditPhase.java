package org.ton.java.tlb;

import java.io.Serializable;
import java.math.BigInteger;
import lombok.Builder;
import lombok.Data;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.cell.CellSlice;

/**
 *
 *
 * <pre>
 * tr_phase_credit$_
 *  due_fees_collected:(Maybe Grams)
 *  credit:CurrencyCollection = TrCreditPhase;
 *  </pre>
 */
@Builder
@Data
public class CreditPhase implements Serializable {
  BigInteger dueFeesCollected;
  CurrencyCollection credit;

  public Cell toCell() {
    return CellBuilder.beginCell()
        .storeCoinsMaybe(dueFeesCollected)
        .storeSlice(CellSlice.beginParse(credit.toCell()))
        .endCell();
  }

  public static CreditPhase deserialize(CellSlice cs) {
    return CreditPhase.builder()
        .dueFeesCollected(cs.loadBit() ? cs.loadCoins() : null)
        .credit(CurrencyCollection.deserialize(cs))
        .build();
  }
}
