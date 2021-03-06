package site.binghai.coin.cron;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import site.binghai.coin.common.entity.Kline;
import site.binghai.coin.common.entity.KlineTime;
import site.binghai.coin.common.response.Symbol;
import site.binghai.coin.common.utils.CoinUtils;
import site.binghai.coin.common.utils.CommonUtils;
import site.binghai.coin.common.utils.MathUtils;
import site.binghai.coin.data.impl.MemberCacheService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static site.binghai.coin.common.utils.CommonUtils.removeZero;

/**
 * Created by binghai on 2018/2/22.
 * 荒野大镖客
 *
 * @ huobi
 */
@Component
public class A_FistfulOfDollars implements ApplicationListener<ContextRefreshedEvent> {
    private final Logger logger = LoggerFactory.getLogger(A_FistfulOfDollars.class);

    @Autowired
    private MemberCacheService memberCacheService;

    @Scheduled(cron = "0 0 * * * ?")
    public void work() {
        logger.info("STARTED.");
        JSONArray array = new JSONArray();

        List<FcItem> fcs = new ArrayList<>(); // 方差
        List<Symbol> symbols = CoinUtils.allSymbols();
        if(CollectionUtils.isEmpty(symbols)) return;
        symbols.parallelStream()
//                .peek(System.out::println)
                .filter(v -> v.getQuoteCurrency().equals("usdt"))
                .forEach(v -> deal(fcs, v));

        fcs.sort((a, b) -> CommonUtils.cmpDouble2int(b.getFc() - a.getFc()));

        fcs.stream().limit(10).forEach(v -> {
            JSONObject block = new JSONObject();

            block.put("symbol", String.valueOf(v.getSymbol().getBaseCurrency() + "/" + v.getSymbol().getQuoteCurrency()).toUpperCase());
            block.put("floatValue", removeZero(v.getFc()));
            block.put("maxValue", removeZero(v.getMax()));
            block.put("minValue", removeZero(v.getMin()));
            block.put("avgValue", removeZero(v.getAvg()));
            block.put("sugPrice", removeZero(v.getAvg() / 1.015));
            double cur = CoinUtils.getLastestKline(v.getSymbol()).getClose();
            block.put("curPrice", cur);
            block.put("diffPrice", removeZero(cur - Double.parseDouble(block.getString("sugPrice"))));
            block.put("estimatedIncome", removeZero((v.getAvg() / v.getMin()) * 100 - 100) + "%");

            array.add(block);
        });

        memberCacheService.put(MemberCacheService.CacheKeys.FLOAT_TOP_10, array);
    }

    /**
     * 取出近两小时的分钟级数据
     * 求震荡幅度
     */
    private void deal(List<FcItem> fc, Symbol symbol) {
        List<Kline> klines = CoinUtils.getKlineList(symbol, KlineTime.MIN1, 120);
        if (CollectionUtils.isEmpty(klines)) {
            return;
        }

        FcItem fcItem = new FcItem();
        double avg = klines.stream().collect(Collectors.averagingDouble(Kline::getClose));
        List<Double> high = klines.stream().map(Kline::getHigh).sorted().collect(Collectors.toList());
        List<Double> low = klines.stream().map(Kline::getLow).sorted().collect(Collectors.toList());

//        logger.info("high:{},{}", high.size(),high);
//        logger.info("low:{},{}", low.size(),low);

        fcItem.setFc((variance(avg, high) + variance(avg, low)) / 2);
        fcItem.setMax(high.get(high.size() - 1));
        fcItem.setMin(low.get(0));
        fcItem.setSymbol(symbol);
        fcItem.setAvg(avg);

//        logger.info("{}:{}",symbol,fcItem);
        fc.add(fcItem);
    }

    public static double variance(double base, List<Double> values) {
        double r = values.parallelStream()
                .map(v -> v / base)
                .reduce(0.0, Double::sum);

        return r / values.size();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        work();
    }

    @Data
    class FcItem implements Comparator<FcItem> {
        double max = 0.0;
        double min = 0.0;
        double fc = 0.0;
        double avg = 0.0;
        Symbol symbol;

        @Override
        public int compare(FcItem o1, FcItem o2) {
            return CommonUtils.cmpDouble2int(o2.getFc() - o1.getFc());
        }
    }
}
