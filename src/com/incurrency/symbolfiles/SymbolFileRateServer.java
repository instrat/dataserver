/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.symbolfiles;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.RedisConnect;
import com.incurrency.framework.Utilities;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author psharma
 */
public class SymbolFileRateServer {

    private static final Logger logger = Logger.getLogger(SymbolFileRateServer.class.getName());

    public static JedisPool RedisConnect(String uri, Integer port, Integer database) {
        return new JedisPool(new JedisPoolConfig(), uri, port, 10000, null, database);
    }

    private RedisConnect jPool;
    private String currentDay;
    private List<BeanSymbol> nifty50 = new ArrayList<>();
    private List<BeanSymbol> symbols = new ArrayList<>();
    private List<BeanSymbol> cnx500 = new ArrayList<>();
    private String symbolFileName;

    public SymbolFileRateServer(String redisurl, String symbolFileName) {
        this.symbolFileName = symbolFileName;
        jPool = new RedisConnect(redisurl.split(":")[0], Integer.valueOf(redisurl.split(":")[1]), Integer.valueOf(redisurl.split(":")[2]));
        currentDay = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(MainAlgorithm.timeZone));
        loadAllSymbols();
        nifty50 = loadForeverNifty50Stocks();
        cnx500 = loadCNX500Stocks();
        rateserver();
    }

    public void rateserver() {
        ArrayList<BeanSymbol> out = new ArrayList<>();

        //NSENIFTY and Index is priority 1
        //FNO stocks in NIFTY are priority 2
        //Residual F&O Stocks are priority 3
        String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0,Algorithm.timeZone);
        BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        out.add(s);
        s = new BeanSymbol("BANKNIFTY", "BANKNIFTY", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(40);
        s.setStrikeDistance(100);
        out.add(s);
        
        s = new BeanSymbol("TRIN-NSE", "TRIN-NSE", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        out.add(s);

        s = new BeanSymbol("TICK-NSE", "TICK-NSE", "IND", "", "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        out.add(s);

        
        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(75);
        s.setStrikeDistance(100);
        out.add(s);
        //Date dtExpiry = DateUtil.parseDate("yyyyMMdd", expiry, MainAlgorithm.timeZone);
        //String expiryplus = DateUtil.getFormatedDate("yyyyMMdd", DateUtil.addDays(dtExpiry, 1).getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        String nextExpiry = Utilities.getLastThursday(expiry, "yyyyMMdd", 1,Algorithm.timeZone);

        s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", nextExpiry, "", "");
        s.setCurrency("INR");
        s.setExchange("NSE");
        s.setStreamingpriority(1);
        s.setStrategy("DATA");
        s.setMinsize(75);
        s.setStrikeDistance(100);
        out.add(s);

        //Add nifty stocks. Priority =1
        for (int i = 0; i < nifty50.size(); i++) {
            nifty50.get(i).setStreamingpriority(1);
            nifty50.get(i).setStrategy("DATA");
        }

        out.addAll(nifty50);

        //Add F&O Stocks on Nifty50. Priority = 2
        // Other F&O, Priority 3
        ArrayList<BeanSymbol> fno = loadFutures(expiry);
        for (int i = 0; i < fno.size(); i++) {
            String exchangesymbol = fno.get(i).getExchangeSymbol();
//            int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
           int id = Utilities.getIDFromExchangeSymbol(symbols, exchangesymbol, "STK", "", "", "");
            if (id >= 0) {
//                id = Utilities.getIDFromExchangeSymbol(fno, exchangesymbol, "FUT", expiry, "", "");
//                s = fno.get(id);
                      int dupid = Utilities.getIDFromExchangeSymbol(out, exchangesymbol, "STK", "", "", "");                  
                      if(dupid<0){// add symbol if not already present
                s=symbols.get(id);
                BeanSymbol s1 = s.clone(s);
                s1.setStreamingpriority(2);
                s1.setStrategy("DATA");
//                s1.setExpiry(expiry);
//                s1.setType("FUT");
                out.add(s1);
            }
//                      else {
//                id = Utilities.getIDFromExchangeSymbol(fno, exchangesymbol, "FUT", expiry, "", "");
//                s = fno.get(id);
//                BeanSymbol s1 = s.clone(s);
//                s1.setStreamingpriority(3);
//                s1.setStrategy("DATA");
//                out.add(s1);
//            }
        }
//        ArrayList<BeanSymbol> fwdout = loadFutures(nextExpiry);
//        for (int i = 0; i < fwdout.size(); i++) {
//            String exchangesymbol = fwdout.get(i).getExchangeSymbol();
//            int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
//            if (id >= 0) {
//                id = Utilities.getIDFromExchangeSymbol(fwdout, exchangesymbol, "FUT", nextExpiry, "", "");
//                s = fwdout.get(id);
//                BeanSymbol s1 = s.clone(s);
//                s1.setStreamingpriority(2);
//                s1.setStrategy("DATA");
//                s1.setExpiry(nextExpiry);
//                s1.setType("FUT");
//                out.add(s1);
//            } else {
//                id = Utilities.getIDFromExchangeSymbol(fwdout, exchangesymbol, "FUT", nextExpiry, "", "");
//                s = fwdout.get(id);
//                BeanSymbol s1 = s.clone(s);
//                s1.setStreamingpriority(3);
//                s1.setStrategy("DATA");
//                out.add(s1);
//            }
        }

        Utilities.printSymbolsToFile(out, symbolFileName, false);
    }

    public void loadAllSymbols() {
        String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        String shortlistedkey = Utilities.getShorlistedKey(jPool, "ibsymbols", today);
        Map<String, String> ibsymbols = new HashMap<>();
        try (Jedis jedis = jPool.pool.getResource()) {
            ibsymbols = jedis.hgetAll(shortlistedkey);
            for (Map.Entry<String, String> entry : ibsymbols.entrySet()) {
                String exchangeSymbol = entry.getKey().trim().toUpperCase();
                String brokerSymbol = entry.getValue().trim().toUpperCase();
                BeanSymbol tempContract = new BeanSymbol();
                tempContract.setExchange("NSE");
                tempContract.setCurrency("INR");
                tempContract.setType("STK");
                tempContract.setExchangeSymbol(exchangeSymbol);
                tempContract.setBrokerSymbol(brokerSymbol);
                tempContract.setSerialno(symbols.size());
                symbols.add(tempContract);
            }

        }

    }

    public ArrayList<BeanSymbol> loadNifty50Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "nifty50", today);
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    } else {
                        logger.log(Level.SEVERE, "500,NIFTY50 symbol not found in ibsymbols,{0}:{1}:{2}:{3}:{4},SymbolNotFound={5}",
                                new Object[]{"Unknown", "Unknown", "Unknown", -1, -1, exchangeSymbol});
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i);
            }

            //Capture Strike levels
            String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0,Algorithm.timeZone);;
            shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadForeverNifty50Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));

            String cursor = "";
            int date = 0;
            Set<String> foreverNifty50 = new HashSet<>();
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.pool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("nifty50")) {
                            foreverNifty50.addAll(jedis.smembers(key.toString()));
                        }
                    }
                }
            }
            //select stocks that are in fno
            String fnokey = Utilities.getShorlistedKey(jPool, "contractsize", today);
            Map<String, String> contractSizes = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                contractSizes = jedis.hgetAll(fnokey);
            }
            //select intersection of ForeverNifty50 & contractSizes("key")
            foreverNifty50.retainAll(contractSizes.keySet());
            Iterator iterator = foreverNifty50.iterator();
            while (iterator.hasNext()) {
                String exchangeSymbol = iterator.next().toString().toUpperCase();
                int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                if (id >= 0) {
                    BeanSymbol s = symbols.get(id);
                    BeanSymbol s1 = s.clone(s);
                    out.add(s1);
                } else {
                    logger.log(Level.SEVERE, "500,NIFTY50 symbol not found in ibsymbols,{0}:{1}:{2}:{3}:{4},SymbolNotFound={5}",
                            new Object[]{"Unknown", "Unknown", "Unknown", -1, -1, exchangeSymbol});
                }
            }

            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i);
            }

            //Capture Strike levels
            String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0,Algorithm.timeZone);;
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadFutures(String expiry) {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        ArrayList<BeanSymbol> interimout = new ArrayList<>();
        try {
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "contractsize", expiry);
            Map<String, String> contractSizes = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                contractSizes = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : contractSizes.entrySet()) {
                    String exchangeSymbol = entry.getKey().trim().toUpperCase();
                    int minsize = Utilities.getInt(entry.getValue().trim(), 0);
                    if (minsize > 0) {
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                        if (id >= 0) {
                            BeanSymbol s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("FUT");
                            s1.setExpiry(expiry);
                            s1.setMinsize(minsize);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(2);
                            s1.setSerialno(out.size());
                            interimout.add(s1);
                        } else {
                            logger.log(Level.SEVERE, "Exchange Symbol {0} not found in IB database", new Object[]{exchangeSymbol});
                        }
                    }
                }
            }

            //Fix sequential serial numbers
            for (int i = 0; i < interimout.size(); i++) {
                interimout.get(i).setSerialno(i);
            }

            //Capture Strike levels
            shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(interimout, exchangeSymbol, "FUT", expiry, "", "");
                    if (id >= 0) {
                        BeanSymbol s = interimout.get(id);
                        BeanSymbol s1 = s.clone(s);
                        s1.setType("FUT");
                        s1.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                        out.add(s1);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setSerialno(i);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadCNX500Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String today = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
            String shortlistedkey = Utilities.getShorlistedKey(jPool, "cnx500", today);
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i);
            }

            //Capture Strike levels
            String expiry = Utilities.getLastThursday(currentDay, "yyyyMMdd", 0,Algorithm.timeZone);;
            shortlistedkey = Utilities.getShorlistedKey(jPool, "strikedistance", expiry);
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.pool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;
    }
}
