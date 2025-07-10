package org.ton.ton4j.smartcontract.integrationtests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ton.java.adnl.AdnlLiteClient;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellBuilder;
import org.ton.ton4j.smartcontract.GenerateWallet;
import org.ton.ton4j.smartcontract.token.nft.NftCollection;
import org.ton.ton4j.smartcontract.token.nft.NftItem;
import org.ton.ton4j.smartcontract.token.nft.NftMarketplace;
import org.ton.ton4j.smartcontract.token.nft.NftSale;
import org.ton.ton4j.smartcontract.types.CollectionData;
import org.ton.ton4j.smartcontract.types.Royalty;
import org.ton.ton4j.smartcontract.types.WalletCodes;
import org.ton.ton4j.smartcontract.types.WalletV3Config;
import org.ton.ton4j.smartcontract.wallet.v3.WalletV3R1;
import org.ton.ton4j.tonlib.types.ExtMessageInfo;
import org.ton.ton4j.utils.Utils;

@Slf4j
@RunWith(JUnit4.class)
public class TestNft extends CommonTest {

  private static final String WALLET2_ADDRESS = "EQB6-6po0yspb68p7RRetC-hONAz-JwxG9514IEOKw_llXd5";

  static WalletV3R1 adminWallet;

  static WalletV3R1 nftItemBuyer;

  private Address nftItem1Address;
  private Address nftItem2Address;

  @Test
  public void testNft() throws InterruptedException {

    adminWallet = GenerateWallet.randomV3R1(tonlib, 7);
    nftItemBuyer = GenerateWallet.randomV3R1(tonlib, 3);

    log.info("admin wallet address {}", adminWallet.getAddress());
    log.info("buyer wallet address {}", nftItemBuyer.getAddress());

    NftCollection nftCollection =
        NftCollection.builder()
            .tonlib(tonlib)
            .adminAddress(adminWallet.getAddress())
            .royalty(0.13)
            .royaltyAddress(adminWallet.getAddress())
            .collectionContentUri(
                "https://raw.githubusercontent.com/neodiX42/ton4j/main/1-media/nft-collection.json")
            .collectionContentBaseUri(
                "https://raw.githubusercontent.com/neodiX42/ton4j/main/1-media/")
            .nftItemCodeHex(WalletCodes.nftItem.getValue())
            .build();

    log.info("NFT collection address {}", nftCollection.getAddress());

    // deploy NFT Collection
    WalletV3Config adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftCollection.getAddress())
            .amount(Utils.toNano(1))
            .stateInit(nftCollection.getStateInit())
            .build();

    ExtMessageInfo extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    log.info("deploying NFT collection");

    nftCollection.waitForDeployment(60);

    getNftCollectionInfo(nftCollection);

    Cell body =
        NftCollection.createMintBody(
            0, 0, Utils.toNano(0.06), adminWallet.getAddress(), "nft-item-1.json");

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftCollection.getAddress())
            .amount(Utils.toNano(1))
            .body(body)
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(30, "deploying NFT item #1");

    body =
        NftCollection.createMintBody(
            0, 1, Utils.toNano(0.07), adminWallet.getAddress(), "nft-item-2.json");

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftCollection.getAddress())
            .amount(Utils.toNano(1))
            .body(body)
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(40, "deploying NFT item #2");

    assertThat(getNftCollectionInfo(nftCollection)).isEqualTo(2);

    NftMarketplace marketplace =
        NftMarketplace.builder().adminAddress(adminWallet.getAddress()).build();

    log.info("nft marketplace address {}", marketplace.getAddress());

    // deploy own NFT marketplace
    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(marketplace.getAddress())
            .amount(Utils.toNano(1))
            .stateInit(marketplace.getStateInit())
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(30, "deploying nft marketplace");

    // deploy nft sale for item 1
    NftSale nftSale1 =
        NftSale.builder()
            .marketplaceAddress(marketplace.getAddress())
            .nftItemAddress(nftItem1Address)
            .fullPrice(Utils.toNano(1.1))
            .marketplaceFee(Utils.toNano(0.4))
            .royaltyAddress(nftCollection.getAddress())
            .royaltyAmount(Utils.toNano(0.3))
            .build();

    log.info("nft-sale-1 address {}", nftSale1.getAddress());

    body =
        CellBuilder.beginCell()
            .storeUint(1, 32)
            .storeCoins(Utils.toNano(0.06))
            .storeRef(nftSale1.getStateInit().toCell())
            .storeRef(CellBuilder.beginCell().endCell())
            .endCell();

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(marketplace.getAddress())
            .amount(Utils.toNano(0.06))
            .body(body)
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    Utils.sleep(40, "deploying NFT sale smart-contract for nft item #1");

    // get nft item 1 data
    log.info("nftSale data for nft item #1 {}", nftSale1.getData(tonlib));

    // deploy nft sale for item 2 -----------------------------------------------------------
    NftSale nftSale2 =
        NftSale.builder()
            .marketplaceAddress(marketplace.getAddress())
            .nftItemAddress(Address.of(nftItem2Address))
            .fullPrice(Utils.toNano(1.2))
            .marketplaceFee(Utils.toNano(0.3))
            .royaltyAddress(nftCollection.getAddress())
            .royaltyAmount(Utils.toNano(0.2))
            .build();

    body =
        CellBuilder.beginCell()
            .storeUint(1, 32)
            .storeCoins(Utils.toNano(0.06))
            .storeRef(nftSale2.getStateInit().toCell())
            .storeRef(CellBuilder.beginCell().endCell())
            .endCell();

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(marketplace.getAddress())
            .amount(Utils.toNano(0.06))
            .body(body)
            .build();

    log.info("nft-sale-2 address {}", nftSale2.getAddress().toString(true, true, true));
    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    Utils.sleep(40, "deploying NFT sale smart-contract for nft item #2");

    // get nft item 2 data
    log.info("nftSale data for nft item #2 {}", nftSale2.getData(tonlib));

    // sends from adminWallet to nftItem request for static data, response comes to adminWallet
    // https://github.com/ton-blockchain/token-contract/blob/main/nft/nft-item.fc#L131

    getStaticData(adminWallet, Utils.toNano(0.088), nftItem1Address, BigInteger.valueOf(661));

    // transfer nft item to nft sale smart-contract (send amount > full_price+1ton)
    transferNftItem(
        adminWallet,
        Utils.toNano(1.4),
        nftItem1Address,
        BigInteger.ZERO,
        nftSale1.getAddress(),
        Utils.toNano(0.02),
        // "gift1".getBytes(),
        null,
        adminWallet.getAddress());
    Utils.sleep(35, "transferring item-1 to nft-sale-1 and waiting for seqno update");

    transferNftItem(
        adminWallet,
        Utils.toNano(1.5),
        nftItem2Address,
        BigInteger.ZERO,
        nftSale2.getAddress(),
        Utils.toNano(0.02),
        "gift2".getBytes(),
        adminWallet.getAddress());
    Utils.sleep(35, "transferring item-2 to nft-sale-2 and waiting for seqno update");

    // cancels selling of item1, moves nft-item from nft-sale-1 smc back to adminWallet. nft-sale-1
    // smc becomes uninitialized

    WalletV3Config walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftSale1.getAddress())
            .amount(Utils.toNano(1))
            .body(NftSale.createCancelBody(0))
            .build();
    extMessageInfo = adminWallet.send(walletV3Config);

    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(35, "cancel selling of item1");

    // buy nft-item-2. send fullPrice+minimalGasAmount(1ton)
    walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(nftItemBuyer.getSeqno())
            .destination(nftSale2.getAddress())
            .amount(Utils.toNano(1.2 + 1))
            .build();
    extMessageInfo = nftItemBuyer.send(walletV3Config);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    // after changed owner this will fail with 401 error - current nft collection is not editable,
    // so nothing happens
    editNftCollectionContent(
        adminWallet,
        Utils.toNano(0.055),
        nftCollection.getAddress(),
        "ton://my-nft/collection.json",
        "ton://my-nft/",
        0.16,
        Address.of(WALLET2_ADDRESS),
        adminWallet.getKeyPair());

    changeNftCollectionOwner(
        adminWallet, Utils.toNano(0.06), nftCollection.getAddress(), Address.of(WALLET2_ADDRESS));

    getRoyaltyParams(adminWallet, Utils.toNano(0.0777), nftCollection.getAddress());
  }

  @Test
  public void testNftAdnlLiteClient() throws Exception {

    AdnlLiteClient adnlLiteClient =
        AdnlLiteClient.builder()
            .configUrl(Utils.getGlobalConfigUrlTestnetGithub())
            .liteServerIndex(0)
            .build();

    adminWallet = GenerateWallet.randomV3R1(adnlLiteClient, 7);
    nftItemBuyer = GenerateWallet.randomV3R1(adnlLiteClient, 3);

    log.info("admin wallet address {}", adminWallet.getAddress());
    log.info("buyer wallet address {}", nftItemBuyer.getAddress());

    NftCollection nftCollection =
        NftCollection.builder()
            .adnlLiteClient(adnlLiteClient)
            .adminAddress(adminWallet.getAddress())
            .royalty(0.13)
            .royaltyAddress(adminWallet.getAddress())
            .collectionContentUri(
                "https://raw.githubusercontent.com/neodiX42/ton4j/main/1-media/nft-collection.json")
            .collectionContentBaseUri(
                "https://raw.githubusercontent.com/neodiX42/ton4j/main/1-media/")
            .nftItemCodeHex(WalletCodes.nftItem.getValue())
            .build();

    log.info("NFT collection address {}", nftCollection.getAddress());

    // deploy NFT Collection
    WalletV3Config adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftCollection.getAddress())
            .amount(Utils.toNano(1))
            .stateInit(nftCollection.getStateInit())
            .build();

    ExtMessageInfo extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    log.info("deploying NFT collection");

    nftCollection.waitForDeployment(60);

    getNftCollectionInfo(nftCollection);

    Cell body =
        NftCollection.createMintBody(
            0, 0, Utils.toNano(0.06), adminWallet.getAddress(), "nft-item-1.json");

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftCollection.getAddress())
            .amount(Utils.toNano(1))
            .body(body)
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(30, "deploying NFT item #1");

    body =
        NftCollection.createMintBody(
            0, 1, Utils.toNano(0.07), adminWallet.getAddress(), "nft-item-2.json");

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftCollection.getAddress())
            .amount(Utils.toNano(1))
            .body(body)
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(40, "deploying NFT item #2");

    assertThat(getNftCollectionInfo(nftCollection)).isEqualTo(2);

    NftMarketplace marketplace =
        NftMarketplace.builder().adminAddress(adminWallet.getAddress()).build();

    log.info("nft marketplace address {}", marketplace.getAddress());

    // deploy own NFT marketplace
    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(marketplace.getAddress())
            .amount(Utils.toNano(1))
            .stateInit(marketplace.getStateInit())
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(30, "deploying nft marketplace");

    // deploy nft sale for item 1
    NftSale nftSale1 =
        NftSale.builder()
            .marketplaceAddress(marketplace.getAddress())
            .nftItemAddress(nftItem1Address)
            .fullPrice(Utils.toNano(1.1))
            .marketplaceFee(Utils.toNano(0.4))
            .royaltyAddress(nftCollection.getAddress())
            .royaltyAmount(Utils.toNano(0.3))
            .build();

    log.info("nft-sale-1 address {}", nftSale1.getAddress());

    body =
        CellBuilder.beginCell()
            .storeUint(1, 32)
            .storeCoins(Utils.toNano(0.06))
            .storeRef(nftSale1.getStateInit().toCell())
            .storeRef(CellBuilder.beginCell().endCell())
            .endCell();

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(marketplace.getAddress())
            .amount(Utils.toNano(0.06))
            .body(body)
            .build();

    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    Utils.sleep(40, "deploying NFT sale smart-contract for nft item #1");

    // get nft item 1 data
    log.info("nftSale data for nft item #1 {}", nftSale1.getData(tonlib));

    // deploy nft sale for item 2 -----------------------------------------------------------
    NftSale nftSale2 =
        NftSale.builder()
            .marketplaceAddress(marketplace.getAddress())
            .nftItemAddress(Address.of(nftItem2Address))
            .fullPrice(Utils.toNano(1.2))
            .marketplaceFee(Utils.toNano(0.3))
            .royaltyAddress(nftCollection.getAddress())
            .royaltyAmount(Utils.toNano(0.2))
            .build();

    body =
        CellBuilder.beginCell()
            .storeUint(1, 32)
            .storeCoins(Utils.toNano(0.06))
            .storeRef(nftSale2.getStateInit().toCell())
            .storeRef(CellBuilder.beginCell().endCell())
            .endCell();

    adminWalletConfig =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(marketplace.getAddress())
            .amount(Utils.toNano(0.06))
            .body(body)
            .build();

    log.info("nft-sale-2 address {}", nftSale2.getAddress().toString(true, true, true));
    extMessageInfo = adminWallet.send(adminWalletConfig);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    Utils.sleep(40, "deploying NFT sale smart-contract for nft item #2");

    // get nft item 2 data
    log.info("nftSale data for nft item #2 {}", nftSale2.getData(tonlib));

    // sends from adminWallet to nftItem request for static data, response comes to adminWallet
    // https://github.com/ton-blockchain/token-contract/blob/main/nft/nft-item.fc#L131

    getStaticData(adminWallet, Utils.toNano(0.088), nftItem1Address, BigInteger.valueOf(661));

    // transfer nft item to nft sale smart-contract (send amount > full_price+1ton)
    transferNftItem(
        adminWallet,
        Utils.toNano(1.4),
        nftItem1Address,
        BigInteger.ZERO,
        nftSale1.getAddress(),
        Utils.toNano(0.02),
        // "gift1".getBytes(),
        null,
        adminWallet.getAddress());
    Utils.sleep(35, "transferring item-1 to nft-sale-1 and waiting for seqno update");

    transferNftItem(
        adminWallet,
        Utils.toNano(1.5),
        nftItem2Address,
        BigInteger.ZERO,
        nftSale2.getAddress(),
        Utils.toNano(0.02),
        "gift2".getBytes(),
        adminWallet.getAddress());
    Utils.sleep(35, "transferring item-2 to nft-sale-2 and waiting for seqno update");

    // cancels selling of item1, moves nft-item from nft-sale-1 smc back to adminWallet. nft-sale-1
    // smc becomes uninitialized

    WalletV3Config walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(adminWallet.getSeqno())
            .destination(nftSale1.getAddress())
            .amount(Utils.toNano(1))
            .body(NftSale.createCancelBody(0))
            .build();
    extMessageInfo = adminWallet.send(walletV3Config);

    assertThat(extMessageInfo.getError().getCode()).isZero();
    Utils.sleep(35, "cancel selling of item1");

    // buy nft-item-2. send fullPrice+minimalGasAmount(1ton)
    walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(nftItemBuyer.getSeqno())
            .destination(nftSale2.getAddress())
            .amount(Utils.toNano(1.2 + 1))
            .build();
    extMessageInfo = nftItemBuyer.send(walletV3Config);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    // after changed owner this will fail with 401 error - current nft collection is not editable,
    // so nothing happens
    editNftCollectionContent(
        adminWallet,
        Utils.toNano(0.055),
        nftCollection.getAddress(),
        "ton://my-nft/collection.json",
        "ton://my-nft/",
        0.16,
        Address.of(WALLET2_ADDRESS),
        adminWallet.getKeyPair());

    changeNftCollectionOwner(
        adminWallet, Utils.toNano(0.06), nftCollection.getAddress(), Address.of(WALLET2_ADDRESS));

    getRoyaltyParams(adminWallet, Utils.toNano(0.0777), nftCollection.getAddress());
  }

  private long getNftCollectionInfo(NftCollection nftCollection) {
    CollectionData data = nftCollection.getCollectionData(tonlib);
    log.info("nft collection info {}", data);
    log.info("nft collection item count {}", data.getNextItemIndex());
    log.info("nft collection owner {}", data.getOwnerAddress());

    nftItem1Address = nftCollection.getNftItemAddressByIndex(tonlib, BigInteger.ZERO);
    nftItem2Address = nftCollection.getNftItemAddressByIndex(tonlib, BigInteger.ONE);

    log.info("address at index 1 = {}", nftItem1Address);
    log.info("address at index 2 = {}", nftItem2Address);

    Royalty royalty = nftCollection.getRoyaltyParams(tonlib);
    log.info("nft collection royalty address {}", royalty.getRoyaltyAddress());

    return data.getNextItemIndex();
  }

  public void changeNftCollectionOwner(
      WalletV3R1 wallet, BigInteger msgValue, Address nftCollectionAddress, Address newOwner) {

    WalletV3Config walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(wallet.getSeqno())
            .destination(nftCollectionAddress)
            .amount(msgValue)
            .body(NftCollection.createChangeOwnerBody(0, newOwner))
            .build();
    ExtMessageInfo extMessageInfo = wallet.send(walletV3Config);
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  public void editNftCollectionContent(
      WalletV3R1 wallet,
      BigInteger msgValue,
      Address nftCollectionAddress,
      String collectionContentUri,
      String nftItemContentBaseUri,
      double royalty,
      Address royaltyAddress,
      TweetNaclFast.Signature.KeyPair keyPair) {

    WalletV3Config walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(wallet.getSeqno())
            .destination(nftCollectionAddress)
            .amount(msgValue)
            .body(
                NftCollection.createEditContentBody(
                    0, collectionContentUri, nftItemContentBaseUri, royalty, royaltyAddress))
            .build();
    ExtMessageInfo extMessageInfo = wallet.send(walletV3Config);
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  public void getRoyaltyParams(
      WalletV3R1 wallet, BigInteger msgValue, Address nftCollectionAddress) {

    WalletV3Config walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(wallet.getSeqno())
            .destination(nftCollectionAddress)
            .amount(msgValue)
            .body(NftCollection.createGetRoyaltyParamsBody(0))
            .build();
    ExtMessageInfo extMessageInfo = wallet.send(walletV3Config);
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  private void getStaticData(
      WalletV3R1 wallet, BigInteger msgValue, Address nftItemAddress, BigInteger queryId) {
    WalletV3Config config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(wallet.getSeqno())
            .destination(nftItemAddress)
            .amount(msgValue)
            .body(NftItem.createGetStaticDataBody(queryId))
            .build();
    ExtMessageInfo extMessageInfo = wallet.send(config);
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  private void transferNftItem(
      WalletV3R1 wallet,
      BigInteger msgValue,
      Address nftItemAddress,
      BigInteger queryId,
      Address nftSaleAddress,
      BigInteger forwardAmount,
      byte[] forwardPayload,
      Address responseAddress) {

    Cell nftTransferBody =
        NftItem.createTransferBody(
            queryId, nftSaleAddress, forwardAmount, forwardPayload, responseAddress);
    WalletV3Config walletV3Config =
        WalletV3Config.builder()
            .walletId(42)
            .seqno(1)
            .validUntil(3600)
            .destination(nftItemAddress)
            .amount(msgValue)
            .body(nftTransferBody)
            .build();
    Cell cell = wallet.prepareExternalMsg(walletV3Config).toCell();
    log.info("cell {}", cell);
    log.info("cell boc {}", cell.toHex());
    ExtMessageInfo extMessageInfo = wallet.send(walletV3Config);
    log.info("extMsgInfo {}", extMessageInfo);
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  @Test
  public void testNftQuick() {

    byte[] secretKey =
        Utils.hexToSignedBytes("F182111193F30D79D517F2339A1BA7C25FDF6C52142F0F2C1D960A1F1D65E1E4");
    TweetNaclFast.Signature.KeyPair keyPair = TweetNaclFast.Signature.keyPair_fromSeed(secretKey);

    WalletV3R1 adminWallet =
        WalletV3R1.builder().tonlib(tonlib).keyPair(keyPair).wc(0).walletId(42).build();
    Address adminAddress = adminWallet.getAddress();
    log.info("adminAddress {}", adminAddress.toRaw());
    Address nftItem1Address = Address.of("EQBOR8LlQGD38A-VvTSLmXDulBx2bVzGPIX0I9G9un_v3a3B");
    Address nftSale1Address = Address.of("EQCbTs8Leh60JMoVc4HftL6RWvzEcVoUm4ACArQFBt-M15Ue");

    transferNftItem(
        adminWallet,
        Utils.toNano(1.4),
        nftItem1Address,
        BigInteger.ZERO,
        nftSale1Address,
        Utils.toNano(0.02),
        // "gift1".getBytes(),
        null,
        adminWallet.getAddress());
  }
}
