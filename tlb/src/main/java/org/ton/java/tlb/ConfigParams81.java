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
 *     jetton_bridge_prices#_ bridge_burn_fee:Coins bridge_mint_fee:Coins
 *                        wallet_min_tons_for_storage:Coins
 *                        wallet_gas_consumption:Coins
 *                        minter_min_tons_for_storage:Coins
 *                        discover_gas_consumption:Coins = JettonBridgePrices;
 *
 * jetton_bridge_params_v0#00 bridge_address:bits256 oracles_address:bits256 oracles:(HashmapE 256 uint256) state_flags:uint8 burn_bridge_fee:Coins = JettonBridgeParams;
 * jetton_bridge_params_v1#01 bridge_address:bits256 oracles_address:bits256 oracles:(HashmapE 256 uint256) state_flags:uint8 prices:^JettonBridgePrices external_chain_address:bits256 = JettonBridgeParams;
 *
 * _ JettonBridgeParams = ConfigParam 81; // BNB->TON token bridge
 * </pre>
 */
@Builder
@Data
public class ConfigParams81 implements Serializable {
  JettonBridgeParams bnbTonTokenBridge;

  public Cell toCell() {
    return CellBuilder.beginCell().storeCell(bnbTonTokenBridge.toCell()).endCell();
  }

  public static ConfigParams81 deserialize(CellSlice cs) {
    return ConfigParams81.builder().bnbTonTokenBridge(JettonBridgeParams.deserialize(cs)).build();
  }
}
