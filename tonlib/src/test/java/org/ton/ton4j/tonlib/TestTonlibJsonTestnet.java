package org.ton.ton4j.tonlib;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellBuilder;
import org.ton.ton4j.cell.CellSlice;
import org.ton.ton4j.mnemonic.Mnemonic;
import org.ton.ton4j.tlb.Transaction;
import org.ton.ton4j.tonlib.types.*;
import org.ton.ton4j.utils.Utils;

@Slf4j
@RunWith(JUnit4.class)
public class TestTonlibJsonTestnet {

  public static final String TON_FOUNDATION = "EQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqB2N";
  public static final String ELECTOR_ADDRESSS =
      "-1:3333333333333333333333333333333333333333333333333333333333333333";

  Gson gs = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  //  static String tonlibPath = Utils.getTonlibGithubUrl();
  static String tonlibPath =
      Utils.getArtifactGithubUrl("tonlibjson", "v2025.03", "neodix42", "ton");

  //  Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();
  //  Tonlib tonlib = null;

  //  String tonlibPath = "tonlibjson.dll";

  @Test
  public void testGetLiteServerVersion() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    LiteServerVersion liteServerVersion = tonlib.getLiteServerVersion();
    log.info("liteServerVersion {}", liteServerVersion);
    tonlib.destroy();
  }

  @Test
  public void testTonlibGetLast() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    BlockIdExt fullblock = tonlib.getLast().getLast();
    log.info("last {}", fullblock);
    tonlib.destroy();
    assertThat(fullblock).isNotNull();
  }

  @Test
  public void testTonlibGetAllBlockTransactions() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    BlockIdExt fullblock = tonlib.getLast().getLast();
    assertThat(fullblock).isNotNull();

    log.info(fullblock.toString());

    Map<String, RawTransactions> txs = tonlib.getAllBlockTransactions(fullblock, 100, null);
    for (Map.Entry<String, RawTransactions> entry : txs.entrySet()) {
      for (RawTransaction tx : entry.getValue().getTransactions()) {
        if (nonNull(tx.getIn_msg())
            && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {
          log.info(
              "{} <<<<< {} : {} ",
              tx.getIn_msg().getSource().getAccount_address(),
              tx.getIn_msg().getDestination().getAccount_address(),
              Utils.formatNanoValue(tx.getIn_msg().getValue(), 9));
        }
        if (nonNull(tx.getOut_msgs())) {
          for (RawMessage msg : tx.getOut_msgs()) {
            log.info(
                "{} >>>>> {} : {} ",
                msg.getSource().getAccount_address(),
                msg.getDestination().getAccount_address(),
                Utils.formatNanoValue(msg.getValue()));
          }
        }
      }
    }
    tonlib.destroy();
    assertThat(txs.size()).isNotEqualTo(0);
  }

  @Test
  public void testTonlibGetBlockTransactions() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    for (int i = 0; i < 2; i++) {

      MasterChainInfo lastBlock = tonlib.getLast();
      log.info(lastBlock.toString());

      BlockTransactions blockTransactions = tonlib.getBlockTransactions(lastBlock.getLast(), 100);
      log.info(gs.toJson(blockTransactions));

      for (ShortTxId shortTxId : blockTransactions.getTransactions()) {
        Address acccount = Address.of("-1:" + Utils.base64ToHexString(shortTxId.getAccount()));
        log.info(
            "lt {}, hash {}, account {}",
            shortTxId.getLt(),
            shortTxId.getHash(),
            acccount.toString(false));
        RawTransactions rawTransactions =
            tonlib.getRawTransactions(
                acccount.toString(false),
                BigInteger.valueOf(shortTxId.getLt()),
                shortTxId.getHash());
        for (RawTransaction tx : rawTransactions.getTransactions()) {
          if (nonNull(tx.getIn_msg())
              && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {
            log.info(
                "{}, {} <<<<< {} : {} ",
                Utils.toUTC(tx.getUtime()),
                tx.getIn_msg().getSource().getAccount_address(),
                tx.getIn_msg().getDestination().getAccount_address(),
                Utils.formatNanoValue(tx.getIn_msg().getValue()));
          }
          if (nonNull(tx.getOut_msgs())) {
            for (RawMessage msg : tx.getOut_msgs()) {
              log.info(
                  "{}, {} >>>>> {} : {} ",
                  Utils.toUTC(tx.getUtime()),
                  msg.getSource().getAccount_address(),
                  msg.getDestination().getAccount_address(),
                  Utils.formatNanoValue(msg.getValue()));
            }
          }
        }
      }
      Utils.sleep(10, "wait for next block");
    }
    tonlib.destroy();
  }

  @Test
  public void testTonlibGetBlockTransactionsExt() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    for (int i = 0; i < 2; i++) {

      MasterChainInfo lastBlock = tonlib.getLast();

      BlockTransactionsExt blockTransactions =
          tonlib.getBlockTransactionsExt(lastBlock.getLast(), 1000, null);
      log.info("size {}", blockTransactions.getTransactions().size());

      for (RawTransaction txi : blockTransactions.getTransactions()) {
        Transaction tx =
            Transaction.deserialize(CellSlice.beginParse(Cell.fromBocBase64(txi.getData())));
        log.info(
            "tx {} {}", Utils.toUTC(tx.getNow()), Long.toHexString(lastBlock.getLast().getShard()));
      }
      Utils.sleep(10, "wait for next block");
    }
    tonlib.destroy();
  }

  @Test
  public void testTonlibGetTxsByAddress() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    tonlib.updateInitBlock();
    Address address = Address.of(TON_FOUNDATION);

    log.info("address: " + address.toBounceable());

    RawTransactions rawTransactions = tonlib.getRawTransactions(address.toRaw(), null, null);

    log.info("total txs: {}", rawTransactions.getTransactions().size());

    for (RawTransaction tx : rawTransactions.getTransactions()) {
      if (nonNull(tx.getIn_msg())
          && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {
        log.info(
            "{}, {} <<<<< {} : {} ",
            Utils.toUTC(tx.getUtime()),
            tx.getIn_msg().getSource().getAccount_address(),
            tx.getIn_msg().getDestination().getAccount_address(),
            Utils.formatNanoValue(tx.getIn_msg().getValue()));
      }
      if (nonNull(tx.getOut_msgs())) {
        for (RawMessage msg : tx.getOut_msgs()) {
          log.info(
              "{}, {} >>>>> {} : {} ",
              Utils.toUTC(tx.getUtime()),
              msg.getSource().getAccount_address(),
              msg.getDestination().getAccount_address(),
              Utils.formatNanoValue(msg.getValue()));
        }
      }
    }

    assertThat(rawTransactions.getTransactions().size()).isLessThan(20);
    tonlib.destroy();
  }

  @Test
  public void testTonlibGetTxsByAddressTestnet() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address =
        Address.of("0:b52a16ba3735501df19997550e7ed4c41754ee501ded8a841088ce4278b66de4");

    log.info("address: " + address.toBounceable());

    RawTransactions rawTransactions = tonlib.getRawTransactions(address.toRaw(), null, null);

    log.info("total txs: {}", rawTransactions.getTransactions().size());

    for (RawTransaction tx : rawTransactions.getTransactions()) {
      if (nonNull(tx.getIn_msg())
          && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {
        log.info("rawTx {}", tx);
        log.info(
            "{}, {} <<<<< {} : {} ",
            Utils.toUTC(tx.getUtime()),
            tx.getIn_msg().getSource().getAccount_address(),
            tx.getIn_msg().getDestination().getAccount_address(),
            Utils.formatNanoValue(tx.getIn_msg().getValue()));
      }
      if (nonNull(tx.getOut_msgs())) {
        for (RawMessage msg : tx.getOut_msgs()) {
          log.info(
              "{}, {} >>>>> {} : {} ",
              Utils.toUTC(tx.getUtime()),
              msg.getSource().getAccount_address(),
              msg.getDestination().getAccount_address(),
              Utils.formatNanoValue(msg.getValue()));
        }
      }
    }
    tonlib.destroy();
  }

  @Test
  public void testTonlibGetTxsV2ByAddressTestnet() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address =
        Address.of("0:b52a16ba3735501df19997550e7ed4c41754ee501ded8a841088ce4278b66de4");

    log.info("address: " + address.toBounceable());

    RawTransactions rawTransactions =
        tonlib.getRawTransactionsV2(address.toRaw(), null, null, 10, true);

    log.info("total txs: {}", rawTransactions.getTransactions().size());

    for (RawTransaction tx : rawTransactions.getTransactions()) {
      if (nonNull(tx.getIn_msg())
          && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {
        log.info("rawTx {}", tx);
        log.info(
            "{}, {} <<<<< {} : {} ",
            Utils.toUTC(tx.getUtime()),
            tx.getIn_msg().getSource().getAccount_address(),
            tx.getIn_msg().getDestination().getAccount_address(),
            Utils.formatNanoValue(tx.getIn_msg().getValue()));
      }
      if (nonNull(tx.getOut_msgs())) {
        for (RawMessage msg : tx.getOut_msgs()) {
          log.info(
              "{}, {} >>>>> {} : {} ",
              Utils.toUTC(tx.getUtime()),
              msg.getSource().getAccount_address(),
              msg.getDestination().getAccount_address(),
              Utils.formatNanoValue(msg.getValue()));
        }
      }
    }
    tonlib.destroy();
    assertThat(rawTransactions.getTransactions().size()).isLessThan(20);
  }

  @Test
  public void testTonlibGetAllTxsByAddress() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address = Address.of("EQAL66-DGwFvP046ysD_o18wvwt-0A6_aJoVmQpVNIqV_ZvK");

    log.info("address: " + address.toBounceable());

    RawTransactions rawTransactions = tonlib.getAllRawTransactions(address.toRaw(), null, null, 51);

    log.info("total txs: {}", rawTransactions.getTransactions().size());

    for (RawTransaction tx : rawTransactions.getTransactions()) {
      if (nonNull(tx.getIn_msg())
          && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {
        log.info(
            "<<<<< {} - {} : {} ",
            tx.getIn_msg().getSource().getAccount_address(),
            tx.getIn_msg().getDestination().getAccount_address(),
            Utils.formatNanoValue(tx.getIn_msg().getValue()));
      }
      if (nonNull(tx.getOut_msgs())) {
        for (RawMessage msg : tx.getOut_msgs()) {
          log.info(
              ">>>>> {} - {} : {} ",
              msg.getSource().getAccount_address(),
              msg.getDestination().getAccount_address(),
              Utils.formatNanoValue(msg.getValue()));
        }
      }
    }
    tonlib.destroy();
    assertThat(rawTransactions.getTransactions().size()).isLessThan(10);
  }

  @Test
  public void testTonlibGetAllTxsByAddressWithMemo() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address = Address.of("EQCQxq9F4-RSaO-ya7q4CF26yyCaQNY98zgD5ys3ZbbiZdUy");

    log.info("address: " + address.toBounceable());

    RawTransactions rawTransactions = tonlib.getAllRawTransactions(address.toRaw(), null, null, 10);

    log.info("total txs: {}", rawTransactions.getTransactions().size());

    for (RawTransaction tx : rawTransactions.getTransactions()) {
      if (nonNull(tx.getIn_msg())
          && (!tx.getIn_msg().getSource().getAccount_address().equals(""))) {

        String msgBodyText;
        if (nonNull(tx.getIn_msg().getMsg_data().getBody())) {

          Cell c =
              CellBuilder.beginCell()
                  .fromBoc(Utils.base64ToSignedBytes(tx.getIn_msg().getMsg_data().getBody()))
                  .endCell();
          msgBodyText = c.print();
        } else {
          msgBodyText = Utils.base64ToString(tx.getIn_msg().getMsg_data().getText());
        }
        log.info(
            "<<<<< {} - {} : {}, msgBody cell/text {}, memo {}, memoBytes {}",
            tx.getIn_msg().getSource().getAccount_address(),
            tx.getIn_msg().getDestination().getAccount_address(),
            Utils.formatNanoValue(tx.getIn_msg().getValue()),
            StringUtils.normalizeSpace(msgBodyText),
            tx.getIn_msg().getMessage(),
            Utils.bytesToHex(tx.getIn_msg().getMessageBytes()));
      }
      if (nonNull(tx.getOut_msgs())) {
        for (RawMessage msg : tx.getOut_msgs()) {
          String msgBodyText;
          if (nonNull(msg.getMsg_data().getBody())) {
            Cell c =
                CellBuilder.beginCell()
                    .fromBoc(Utils.base64ToSignedBytes(msg.getMsg_data().getBody()))
                    .endCell();
            msgBodyText = c.print();
          } else {
            //                        msgBodyText = Utils.base64ToString(msg.getMessage());
            msgBodyText = msg.getMessage();
          }
          log.info(
              ">>>>> {} - {} : {}, msgBody cell/text {}, memo {}, memoHex {}",
              msg.getSource().getAccount_address(),
              msg.getDestination().getAccount_address(),
              Utils.formatNanoValue(msg.getValue()),
              StringUtils.normalizeSpace(msgBodyText),
              msg.getMessage(),
              msg.getMessageHex());
        }
      }
    }
    tonlib.destroy();
    assertThat(rawTransactions.getTransactions().size()).isLessThan(11);
  }

  /** Create new key pair and sign data using Tonlib library */
  @Test
  public void testTonlibNewKey() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Key key = tonlib.createNewKey();
    log.info(key.toString());
    String pubKey = Utils.base64UrlSafeToHexString(key.getPublic_key());
    byte[] secKey = Utils.base64ToBytes(key.getSecret());

    log.info(pubKey);
    log.info(Utils.bytesToHex(secKey));

    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPairFromSeed(secKey);
    byte[] secKey2 = keyPair.getSecretKey();
    log.info(Utils.bytesToHex(secKey2));
    tonlib.destroy();
    assertThat(Utils.bytesToHex(secKey2).contains(Utils.bytesToHex(secKey))).isTrue();
  }

  /** Encrypt/Decrypt using key */
  @Test
  public void testTonlibEncryptDecryptKey() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    String secret = "Q3i3Paa45H/F/Is+RW97lxW0eikF0dPClSME6nbogm0=";
    String dataToEncrypt = Utils.stringToBase64("ABC");
    Data encrypted = tonlib.encrypt(dataToEncrypt, secret);
    log.info("encrypted {}", encrypted.getBytes());

    Data decrypted = tonlib.decrypt(encrypted.getBytes(), secret);
    String dataDecrypted = Utils.base64ToString(decrypted.getBytes());
    log.info("decrypted {}", dataDecrypted);
    tonlib.destroy();
    assertThat("ABC").isEqualTo(dataDecrypted);
  }

  /** Encrypt/Decrypt with using mnemonic */
  @Test
  public void testTonlibEncryptDecryptMnemonic() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    String base64mnemonic =
        Utils.stringToBase64(
            "centring moist twopenny bursary could carbarn abide flirt ground shoelace songster isomeric pis strake jittery penguin gab guileful lierne salivary songbird shore verbal measures");
    String dataToEncrypt = Utils.stringToBase64("ABC");
    Data encrypted = tonlib.encrypt(dataToEncrypt, base64mnemonic);
    log.info("encrypted {}", encrypted.getBytes());

    Data decrypted = tonlib.decrypt(encrypted.getBytes(), base64mnemonic);
    String dataDecrypted = Utils.base64ToString(decrypted.getBytes());
    tonlib.destroy();
    assertThat("ABC").isEqualTo(dataDecrypted);
  }

  @Test
  public void testTonlibEncryptDecryptMnemonicModule()
      throws NoSuchAlgorithmException, InvalidKeyException {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    String base64mnemonic = Utils.stringToBase64(Mnemonic.generateString(24));

    String dataToEncrypt = Utils.stringToBase64("ABC");
    Data encrypted = tonlib.encrypt(dataToEncrypt, base64mnemonic);
    log.info("encrypted {}", encrypted.getBytes());

    Data decrypted = tonlib.decrypt(encrypted.getBytes(), base64mnemonic);
    String dataDecrypted = Utils.base64ToString(decrypted.getBytes());
    tonlib.destroy();
    assertThat("ABC").isEqualTo(dataDecrypted);
  }

  @Test
  public void testTonlibRunMethodParticipantsList() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address =
        Address.of("-1:3333333333333333333333333333333333333333333333333333333333333333");

    RunResult result = tonlib.runMethod(address, "participant_list");
    log.info(result.toString());
    TvmStackEntryList listResult = (TvmStackEntryList) result.getStack().get(0);
    for (Object o : listResult.getList().getElements()) {
      TvmStackEntryTuple t = (TvmStackEntryTuple) o;
      TvmTuple tuple = t.getTuple();
      TvmStackEntryNumber addr = (TvmStackEntryNumber) tuple.getElements().get(0);
      TvmStackEntryNumber stake = (TvmStackEntryNumber) tuple.getElements().get(1);
      log.info("{}, {}", addr.getNumber(), stake.getNumber());
    }
    assertThat(result.getExit_code()).isZero();
    tonlib.destroy();
  }

  @Test
  public void testTonlibRunMethodActiveElectionId() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address =
        Address.of("-1:3333333333333333333333333333333333333333333333333333333333333333");
    RunResult result = tonlib.runMethod(address, "active_election_id");
    TvmStackEntryNumber electionId = (TvmStackEntryNumber) result.getStack().get(0);
    log.info("electionId: {}", electionId.getNumber());
    tonlib.destroy();
    assertThat(result.getExit_code()).isZero();
  }

  @Test
  public void testTonlibRunMethodPastElectionsId() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address =
        Address.of("-1:3333333333333333333333333333333333333333333333333333333333333333");
    RunResult result = tonlib.runMethod(address, "past_election_ids");
    TvmStackEntryList listResult = (TvmStackEntryList) result.getStack().get(0);
    for (Object o : listResult.getList().getElements()) {
      TvmStackEntryNumber electionId = (TvmStackEntryNumber) o;
      log.info(electionId.getNumber().toString());
    }
    tonlib.destroy();
    assertThat(result.getExit_code()).isZero();
  }

  @Test
  public void testTonlibRunMethodPastElections() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address address =
        Address.of("-1:3333333333333333333333333333333333333333333333333333333333333333");
    RunResult result = tonlib.runMethod(address, "past_elections");
    TvmStackEntryList listResult = (TvmStackEntryList) result.getStack().get(0);
    log.info("pastElections: {}", listResult);
    tonlib.destroy();
    assertThat(result.getExit_code()).isZero();
  }

  @Test
  public void testTonlibGetConfig() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    MasterChainInfo mc = tonlib.getLast();
    Cell c = tonlib.getConfigParam(mc.getLast(), 22);
    log.info(c.print());
    tonlib.destroy();
  }

  @Test
  public void testTonlibGetConfigAll() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Cell c = tonlib.getConfigAll(128);
    log.info(c.print());
    tonlib.destroy();
  }

  @Test
  public void testTonlibUpdateInitBlock() {
    Tonlib tonlib = Tonlib.builder().testnet(true).build();
    tonlib.getLast();
    tonlib.updateInitBlock();
    tonlib.destroy();
  }

  @Test
  public void testTonlibLoadContract() {
    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    AccountAddressOnly address =
        AccountAddressOnly.builder()
            .account_address("EQAPZ3Trml6zO403fnA6fiqbjPw9JcOCSk0OVY6dVdyM2fEM")
            .build();
    long result = tonlib.loadContract(address);
    log.info("result {}", result);
    tonlib.destroy();
  }

  @Test
  public void testTonlibRunMethodComputeReturnedStake() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    Address elector = Address.of(ELECTOR_ADDRESSS);
    RunResult result = tonlib.runMethod(elector, "compute_returned_stake", new ArrayDeque<>());
    log.info("result: {}", result);
    assertThat(result.getExit_code())
        .isEqualTo(2); // error since compute_returned_stake requires an argument

    Deque<String> stack = new ArrayDeque<>();
    Address validatorAddress = Address.of("Ef_sR2c8U-tNfCU5klvd60I5VMXUd_U9-22uERrxrrt3uzYi");
    stack.offer("[num," + validatorAddress.toDecimal() + "]");

    result = tonlib.runMethod(elector, "compute_returned_stake", stack);
    BigInteger returnStake = ((TvmStackEntryNumber) result.getStack().get(0)).getNumber();
    log.info("return stake: {} ", Utils.formatNanoValue(returnStake.longValue()));
    tonlib.destroy();
  }

  @Test
  public void testTonlibLookupBlock() {
    try {
      Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

      MasterChainInfo mcInfo = tonlib.getLast();

      Shards shards = tonlib.getShards(mcInfo.getLast().getSeqno(), 0, 0);
      log.info("shards-- {}", shards.getShards());

      BlockIdExt shard = shards.getShards().get(0);

      BlockIdExt fullblock =
          tonlib.lookupBlock(shard.getSeqno(), shard.getWorkchain(), shard.getShard(), 0, 0);
      tonlib.destroy();
      assertThat(fullblock).isNotNull();
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testTonlibTonTps() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(false).build();

    log.info("tps {}", tonlib.getTps(1));
  }

  @Test
  public void testTonlibTonTpsOneBlock() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(false).build();

    log.info("tps {}", tonlib.getTpsOneBlock());
  }

  @Test
  public void testTonlibGetConfig1s() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(false).build();

    log.info("config0 {}", tonlib.getConfigParam25());
  }

  @Test
  public void testTonlibGetConfigs() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(false).build();

    log.info("config0 {}", tonlib.getConfigParam0());
    log.info("config1 {}", tonlib.getConfigParam1());
    log.info("config2 {}", tonlib.getConfigParam2());
    log.info("config3 {}", tonlib.getConfigParam3());
    log.info("config4 {}", tonlib.getConfigParam4());
    log.info("config5 {}", tonlib.getConfigParam5());
    log.info("config6 {}", tonlib.getConfigParam6());
    log.info("config8 {}", tonlib.getConfigParam8());
    log.info("config9 {}", tonlib.getConfigParam9());

    log.info("config10 {}", tonlib.getConfigParam10());
    log.info("config11 {}", tonlib.getConfigParam11());
    log.info("config12 {}", tonlib.getConfigParam12());
    log.info("config13 {}", tonlib.getConfigParam13());
    log.info("config14 {}", tonlib.getConfigParam14());
    log.info("config15 {}", tonlib.getConfigParam15());
    log.info("config16 {}", tonlib.getConfigParam16());
    log.info("config17 {}", tonlib.getConfigParam17());
    log.info("config18 {}", tonlib.getConfigParam18());
    //    log.info("config19 {}", tonlib.getConfigParam19()); // weird

    log.info("config20 {}", tonlib.getConfigParam20());
    log.info("config21 {}", tonlib.getConfigParam21());
    log.info("config22 {}", tonlib.getConfigParam22());
    log.info("config23 {}", tonlib.getConfigParam23());
    log.info("config24 {}", tonlib.getConfigParam24());
    log.info("config25 {}", tonlib.getConfigParam25());
    log.info("config28 {}", tonlib.getConfigParam28());
    log.info("config29 {}", tonlib.getConfigParam29());
    log.info("config31 {}", tonlib.getConfigParam31());
    log.info("config32 {}", tonlib.getConfigParam32());

    log.info("config33 {}", tonlib.getConfigParam33());
    log.info("config34 {}", tonlib.getConfigParam34());
    log.info("config35 {}", tonlib.getConfigParam35());
    log.info("config36 {}", tonlib.getConfigParam36());
    log.info("config37 {}", tonlib.getConfigParam37());
    log.info("config39 {}", tonlib.getConfigParam39());
    log.info("config40 {}", tonlib.getConfigParam40()); // misbehaviour_punishment_config_v1

    log.info("config44 {}", tonlib.getConfigParam44());
    log.info("config45 {}", tonlib.getConfigParam45());
    log.info("config71 {}", tonlib.getConfigParam71());
    log.info("config72 {}", tonlib.getConfigParam72());
    log.info("config73 {}", tonlib.getConfigParam73());
    log.info("config79 {}", tonlib.getConfigParam79());
    log.info("config81 {}", tonlib.getConfigParam81());
    log.info("config82 {}", tonlib.getConfigParam82());

    tonlib.destroy();
  }

  @Test
  public void testTonlibElections() {

    Tonlib tonlib = Tonlib.builder().pathToTonlibSharedLib(tonlibPath).testnet(true).build();

    log.info("electionId {}", tonlib.getElectionId());
    log.info(
        "stake {}",
        tonlib.getReturnedStake(
            "76163360e355006dc4f0a82c8a88a93221b9ac669899498e3b58df4ee16bd1b2"));
  }
}
