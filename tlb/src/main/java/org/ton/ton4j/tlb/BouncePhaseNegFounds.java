package org.ton.ton4j.tlb;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellBuilder;
import org.ton.ton4j.cell.CellSlice;

/**
 *
 *
 * <pre>
 * tr_phase_bounce_negfunds$00 = TrBouncePhase;
 * </pre>
 */
@Builder
@Data
public class BouncePhaseNegFounds implements BouncePhase, Serializable {
  int magic;

  @Override
  public Cell toCell() {
    return CellBuilder.beginCell().storeUint(0, 2).endCell();
  }

  public static BouncePhaseNegFounds deserialize(CellSlice cs) {
    long magic = cs.loadUint(2).intValue(); // review, should be 2
    assert (magic == 0b00)
        : "BouncePhaseNegFounds: magic not equal to 0b00, found 0x" + Long.toHexString(magic);

    return BouncePhaseNegFounds.builder().build();
  }
}
