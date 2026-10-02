package com.polymarket.client;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.DecimalFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    final int BG=Color.rgb(7,11,17), PANEL=Color.rgb(17,23,32), PANEL2=Color.rgb(23,31,43), TEXT=Color.rgb(242,246,250), MUTED=Color.rgb(150,162,178), GOOD=Color.rgb(35,211,105), BAD=Color.rgb(245,82,82), BLUE=Color.rgb(55,125,245), ORANGE=Color.rgb(245,146,35), LINE=Color.rgb(43,53,67);
    LinearLayout root,content; TextView status,marketTitle,btcText,targetText,yesText,noText,yesBookText,noBookText,feedState,reqText,signalText,feedLog,orderInfo,scannerStatus,timeLeftText;
    PolymarketApi api=new PolymarketApi(); ClobAuthManager auth; PolymarketApi.Market selected;
    boolean feedLive=false,paper=true; boolean automationEnabled=false; boolean automationLiveRequested=false; boolean liveTradingArmed=false; String automationSide="YES"; double automationAmount=100; long automationCooldownSec=30, lastAutoOrderMs=0; String automationStatus="OFF"; String lastAutoMarketId=""; double currentYes=-1,currentNo=-1,yesBid=-1,yesAsk=-1,noBid=-1,noAsk=-1,btc=-1; long lastSample=0;
    String strategySide="YES";
    int jumps=0; boolean dropped=false,reversed=false; final ArrayDeque<Double> hist=new ArrayDeque<>();
    int yesJumps=0,noJumps=0; boolean yesDropped=false,yesReversed=false,noDropped=false,noReversed=false;
    final ArrayDeque<Double> yesHist=new ArrayDeque<>(), noHist=new ArrayDeque<>();
    EditText orderAmount; DecimalFormat df=new DecimalFormat("0.00"); Handler timer=new Handler(Looper.getMainLooper());
    double dropMax=.40,dropMin=.35,reversal=.60,confirmation=.60; int requiredJumps=3; long windowMax=180,windowMin=60;
    double yesDropMax=.40,yesDropMin=.35,yesReversal=.60,yesConfirmation=.60; int yesRequiredJumps=3; long yesWindowMax=180,yesWindowMin=60;
    double noDropMax=.40,noDropMin=.35,noReversal=.60,noConfirmation=.60; int noRequiredJumps=3; long noWindowMax=180,noWindowMin=60;
    Runnable btcFallbackLoop;
    Runnable stateLoop;
    Runnable marketLoop; Runnable uiDispatch; boolean uiUpdatePending=false; long lastTargetFetchMs=0; boolean performanceVisible=false;
    final ArrayList<PaperTrade> paperTrades=new ArrayList<>(); long lastPaperResolutionCheckMs=0;

    static class PaperTrade {
        String id,marketId,question,side; double amount,entryPrice,shares,resultPrice,pnl; long createdMs,closeMs; boolean resolved,won;
        JSONObject toJson() throws JSONException {JSONObject o=new JSONObject();o.put("id",id);o.put("marketId",marketId);o.put("question",question);o.put("side",side);o.put("amount",amount);o.put("entryPrice",entryPrice);o.put("shares",shares);o.put("resultPrice",resultPrice);o.put("pnl",pnl);o.put("createdMs",createdMs);o.put("closeMs",closeMs);o.put("resolved",resolved);o.put("won",won);return o;}
        static PaperTrade fromJson(JSONObject o){try{PaperTrade t=new PaperTrade();t.id=o.optString("id","");t.marketId=o.optString("marketId","");t.question=o.optString("question","");t.side=o.optString("side","");t.amount=o.optDouble("amount",0);t.entryPrice=o.optDouble("entryPrice",0);t.shares=o.optDouble("shares",0);t.resultPrice=o.optDouble("resultPrice",0);t.pnl=o.optDouble("pnl",0);t.createdMs=o.optLong("createdMs",0);t.closeMs=o.optLong("closeMs",0);t.resolved=o.optBoolean("resolved",false);t.won=o.optBoolean("won",false);return t;}catch(Exception e){return null;}}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        auth=new ClobAuthManager(this);
        loadPrefs();
        btcFallbackLoop=()->{ refreshBtcFallback(); timer.postDelayed(btcFallbackLoop,3000); };
        stateLoop=()->{ sampleState(); timer.postDelayed(stateLoop,2000); };
        marketLoop=()->{ if(selected==null || remainingSeconds()<0 || !isFiveMinuteSelected()) scanAndSelect(); timer.postDelayed(marketLoop,15000); };
        buildShell();
        api.connectCryptoBtcFeed((p,source,e)->{if(p>0){btc=p;capturePriceToBeatIfAvailable();updateUi();}});
        timer.post(stateLoop);
        timer.post(btcFallbackLoop);
        timer.postDelayed(this::scanAndSelect,700);
        timer.postDelayed(marketLoop,15000);
        showDashboard();
    }
    void loadPrefs(){
        android.content.SharedPreferences p=getSharedPreferences("strategy",MODE_PRIVATE);
        yesDropMax=p.getFloat("yesDropMax",p.getFloat("dropMax",.40f)); yesDropMin=p.getFloat("yesDropMin",p.getFloat("dropMin",.35f));
        yesReversal=p.getFloat("yesReversal",p.getFloat("reversal",.60f)); yesConfirmation=p.getFloat("yesConfirmation",p.getFloat("confirmation",.60f));
        yesRequiredJumps=p.getInt("yesJumps",p.getInt("jumps",3)); yesWindowMax=p.getLong("yesWmax",p.getLong("wmax",180)); yesWindowMin=p.getLong("yesWmin",p.getLong("wmin",60));
        noDropMax=p.getFloat("noDropMax",.40f); noDropMin=p.getFloat("noDropMin",.35f); noReversal=p.getFloat("noReversal",.60f); noConfirmation=p.getFloat("noConfirmation",.60f);
        noRequiredJumps=p.getInt("noJumps",3); noWindowMax=p.getLong("noWmax",180); noWindowMin=p.getLong("noWmin",60);
        strategySide=p.getString("strategySide","YES");
        yesDropped=p.getBoolean("yesDropped",false); yesReversed=p.getBoolean("yesReversed",false); yesJumps=p.getInt("yesJumpState",0);
        noDropped=p.getBoolean("noDropped",false); noReversed=p.getBoolean("noReversed",false); noJumps=p.getInt("noJumpState",0);
        automationEnabled=p.getBoolean("autoEnabled",false);automationSide=p.getString("autoSide","YES");automationAmount=p.getFloat("autoAmount",100f);automationCooldownSec=p.getLong("autoCooldown",30);
        loadPaperTrades();
        syncActiveStrategy();
    }
    void loadPaperTrades(){
        paperTrades.clear();
        String raw=getSharedPreferences("paper_performance",MODE_PRIVATE).getString("trades","[]");
        try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){PaperTrade t=PaperTrade.fromJson(a.optJSONObject(i));if(t!=null)paperTrades.add(t);}}catch(Exception ignored){}
    }
    void savePaperTrades(){
        try{JSONArray a=new JSONArray();int start=Math.max(0,paperTrades.size()-200);for(int i=start;i<paperTrades.size();i++)a.put(paperTrades.get(i).toJson());getSharedPreferences("paper_performance",MODE_PRIVATE).edit().putString("trades",a.toString()).apply();}catch(Exception ignored){}
    }
    void savePrefs(){
        saveActiveStrategy();
        getSharedPreferences("strategy",MODE_PRIVATE).edit()
            .putFloat("yesDropMax",(float)yesDropMax).putFloat("yesDropMin",(float)yesDropMin).putFloat("yesReversal",(float)yesReversal).putFloat("yesConfirmation",(float)yesConfirmation).putInt("yesJumps",yesRequiredJumps).putLong("yesWmax",yesWindowMax).putLong("yesWmin",yesWindowMin)
            .putFloat("noDropMax",(float)noDropMax).putFloat("noDropMin",(float)noDropMin).putFloat("noReversal",(float)noReversal).putFloat("noConfirmation",(float)noConfirmation).putInt("noJumps",noRequiredJumps).putLong("noWmax",noWindowMax).putLong("noWmin",noWindowMin)
            .putString("strategySide",strategySide).putBoolean("yesDropped",yesDropped).putBoolean("yesReversed",yesReversed).putInt("yesJumpState",yesJumps).putBoolean("noDropped",noDropped).putBoolean("noReversed",noReversed).putInt("noJumpState",noJumps)
            .putBoolean("autoEnabled",automationEnabled).putString("autoSide",automationSide).putFloat("autoAmount",(float)automationAmount).putLong("autoCooldown",automationCooldownSec).apply();
    }
    void syncActiveStrategy(){
        if("NO".equals(strategySide)){dropMax=noDropMax;dropMin=noDropMin;reversal=noReversal;confirmation=noConfirmation;requiredJumps=noRequiredJumps;windowMax=noWindowMax;windowMin=noWindowMin;dropped=noDropped;reversed=noReversed;jumps=noJumps;hist.clear();hist.addAll(noHist);}
        else {strategySide="YES";dropMax=yesDropMax;dropMin=yesDropMin;reversal=yesReversal;confirmation=yesConfirmation;requiredJumps=yesRequiredJumps;windowMax=yesWindowMax;windowMin=yesWindowMin;dropped=yesDropped;reversed=yesReversed;jumps=yesJumps;hist.clear();hist.addAll(yesHist);}
    }
    void saveActiveStrategy(){
        if("NO".equals(strategySide)){noDropMax=dropMax;noDropMin=dropMin;noReversal=reversal;noConfirmation=confirmation;noRequiredJumps=requiredJumps;noWindowMax=windowMax;noWindowMin=windowMin;noDropped=dropped;noReversed=reversed;noJumps=jumps;noHist.clear();noHist.addAll(hist);}
        else {yesDropMax=dropMax;yesDropMin=dropMin;yesReversal=reversal;yesConfirmation=confirmation;yesRequiredJumps=requiredJumps;yesWindowMax=windowMax;yesWindowMin=windowMin;yesDropped=dropped;yesReversed=reversed;yesJumps=jumps;yesHist.clear();yesHist.addAll(hist);}
    }
    void switchStrategy(String side){saveActiveStrategy();strategySide="NO".equals(side)?"NO":"YES";syncActiveStrategy();savePrefs();updateUi();showDashboard();}

    TextView tv(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(TEXT);t.setTextSize(size);t.setPadding(12,8,12,8);t.setIncludeFontPadding(true);return t;}
    TextView label(String s){TextView t=tv(s,12);t.setTextColor(MUTED);return t;}
    TextView value(String s,int size,int color){TextView t=tv(s,size);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setAllCaps(false);b.setTextSize(14);b.setGravity(Gravity.CENTER);b.setMinHeight(58);b.setMinimumHeight(58);b.setPadding(8,4,8,4);b.setIncludeFontPadding(true);return b;}
    Button actionBtn(String s,int color){Button b=btn(s);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(14);g.setStroke(1,Color.argb(50,255,255,255));b.setBackground(g);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(68);b.setMinimumHeight(68);return b;}
    Button tabBtn(String s){Button b=btn(s);b.setTextSize(12);b.setMinHeight(46);b.setMinimumHeight(46);GradientDrawable g=new GradientDrawable();g.setColor(PANEL2);g.setCornerRadius(10);b.setBackground(g);return b;}
    LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    LinearLayout box(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(12,12,12,12);c.setBackgroundColor(PANEL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,5,0,5);content.addView(c,lp);return c;}
    void clear(){content.removeAllViews();performanceVisible=false;}
    void section(String s){TextView t=tv(s,18);t.setTextColor(BLUE);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(12,12,12,8);content.addView(t);}

    void buildShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);
        LinearLayout head=row();head.setPadding(12,4,12,2);TextView logo=value("◈  PolymarketClient",19,TEXT);head.addView(logo,new LinearLayout.LayoutParams(0,52,1));status=tv("● PAPER  •  DISCONNECTED",11);status.setTextColor(MUTED);status.setGravity(Gravity.CENTER_VERTICAL);head.addView(status,new LinearLayout.LayoutParams(-2,52));root.addView(head);
        HorizontalScrollView tabsScroll=new HorizontalScrollView(this);tabsScroll.setHorizontalScrollBarEnabled(false);LinearLayout tabs=row();tabs.setPadding(8,2,8,6);
        String[] ns={"LIVE","SCANNER","STRATEGY","AUTOMATION","ORDERS","CLOB KEYS","PERF"}; for(String n:ns){Button b=tabBtn(n);b.setOnClickListener(v->{if(n.equals("LIVE"))showDashboard();else if(n.equals("SCANNER"))showScanner();else if(n.equals("STRATEGY"))showStrategy();else if(n.equals("AUTOMATION"))showAutomation();else if(n.equals("ORDERS"))showExecution();else if(n.equals("CLOB KEYS"))showClobKeys();else showPerformance();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(108,46);lp.setMargins(3,0,3,0);tabs.addView(b,lp);}tabsScroll.addView(tabs);root.addView(tabsScroll);
        ScrollView sc=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(8,4,8,32);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    }

    void showDashboard(){
        clear(); section("MARKET / LIVE ENGINE"); LinearLayout c=box();
        marketTitle=value(selected==null?"NO 5-MIN BTC MARKET SELECTED":selected.question,17,TEXT);c.addView(marketTitle);
        LinearLayout prices=row(); btcText=value("BTC SPOT  —",23,TEXT); prices.addView(btcText,new LinearLayout.LayoutParams(0,-2,1));
        targetText=value("PRICE NEEDED  —",15,GOOD); targetText.setGravity(Gravity.RIGHT); prices.addView(targetText,new LinearLayout.LayoutParams(0,-2,1)); c.addView(prices);
        timeLeftText=label("Scanning for the nearest active 5-minute BTC market…");c.addView(timeLeftText);
        Button scan=actionBtn("SCAN 5-MIN BTC MARKETS",BLUE);scan.setOnClickListener(v->scanAndSelect());c.addView(scan,new LinearLayout.LayoutParams(-1,62));

        LinearLayout prob=row(); LinearLayout y=metric("YES PROBABILITY",GOOD);yesText=value("—",29,GOOD);y.addView(yesText);yesBookText=label("Bid / Ask  — / —");y.addView(yesBookText);prob.addView(y,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout n=metric("NO PROBABILITY",BAD);noText=value("—",29,BAD);n.addView(noText);noBookText=label("Bid / Ask  — / —");n.addView(noBookText);prob.addView(n,new LinearLayout.LayoutParams(0,-2,1));c.addView(prob);
        feedState=value(feedLive?"● CLOB LIVE":"○ CLOB OFFLINE",14,feedLive?GOOD:BAD);c.addView(feedState);
        LinearLayout actions=row();Button connect=actionBtn(feedLive?"DISCONNECT LIVE CLOB":"CONNECT LIVE CLOB",feedLive?Color.rgb(105,55,55):Color.rgb(40,91,145));connect.setOnClickListener(v->{if(selected==null){Toast.makeText(this,"No 5-minute BTC market selected",Toast.LENGTH_SHORT).show();return;}if(feedLive){api.disconnectMarketFeed();feedLive=false;updateUi();}else connectFeed();});actions.addView(connect,new LinearLayout.LayoutParams(0,62,1));Button rescan=actionBtn("RESCAN",PANEL2);rescan.setOnClickListener(v->scanAndSelect());actions.addView(rescan,new LinearLayout.LayoutParams(0,62,1));c.addView(actions);

        section("SIGNAL REQUIREMENTS  •  "+strategySide+" STRATEGY"); LinearLayout req=box();
        LinearLayout strategySwitch=row();
        Button sy=actionBtn("YES STRATEGY", "YES".equals(strategySide)?GOOD:PANEL2);Button sn=actionBtn("NO STRATEGY", "NO".equals(strategySide)?BAD:PANEL2);
        sy.setOnClickListener(v->switchStrategy("YES"));sn.setOnClickListener(v->switchStrategy("NO"));strategySwitch.addView(sy,new LinearLayout.LayoutParams(0,60,1));strategySwitch.addView(sn,new LinearLayout.LayoutParams(0,60,1));req.addView(strategySwitch);
        reqText=tv(requirements(),14);req.addView(reqText);signalText=value("SIGNAL  •  "+signal(),17,"NO".equals(strategySide)?BAD:GOOD);signalText.setPadding(12,14,12,8);req.addView(signalText);

        section("LIVE FEED / 2-SECOND STATE  +  ORDER"); LinearLayout main=row();
        LinearLayout feedCol=new LinearLayout(this);feedCol.setOrientation(LinearLayout.VERTICAL);feedCol.setPadding(4,0,8,0);feedCol.addView(label("LIVE CLOB STATE"));feedLog=tv(feedLines(),12);feedCol.addView(feedLog);main.addView(feedCol,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout orderCol=new LinearLayout(this);orderCol.setOrientation(LinearLayout.VERTICAL);orderCol.setPadding(8,0,4,0);orderCol.setMinimumWidth(150);orderCol.addView(label("ORDER AMOUNT (USD)"));
        LinearLayout amt=row();Button minus=actionBtn("−25",PANEL2);minus.setTextSize(12);minus.setMinHeight(52);minus.setMinimumHeight(52);minus.setOnClickListener(v->adjustAmount(-25));amt.addView(minus,new LinearLayout.LayoutParams(42,54));orderAmount=new EditText(this);orderAmount.setText("100");orderAmount.setTextColor(TEXT);orderAmount.setTextSize(18);orderAmount.setGravity(Gravity.CENTER);orderAmount.setSingleLine(true);orderAmount.setSelectAllOnFocus(true);orderAmount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);orderAmount.setBackgroundColor(Color.TRANSPARENT);LinearLayout.LayoutParams amountLp=new LinearLayout.LayoutParams(64,54);amountLp.setMargins(2,0,2,0);orderAmount.setMinWidth(64);amt.addView(orderAmount,amountLp);Button plus=actionBtn("+25",PANEL2);plus.setTextSize(12);plus.setMinHeight(52);plus.setMinimumHeight(52);plus.setOnClickListener(v->adjustAmount(25));amt.addView(plus,new LinearLayout.LayoutParams(42,54));orderCol.addView(amt);
        LinearLayout quick=row();for(String s:new String[]{"$25","$100","$250","$500"}){Button q=btn(s);q.setTextSize(12);q.setOnClickListener(v->orderAmount.setText(s.substring(1)));quick.addView(q,new LinearLayout.LayoutParams(0,48,1));}orderCol.addView(quick);
        orderInfo=tv(orderPreview(),12);orderCol.addView(orderInfo);main.addView(orderCol,new LinearLayout.LayoutParams(0,-2,1));content.addView(main);
        LinearLayout buys=row();buyButton(buys,true);buyButton(buys,false);content.addView(buys,new LinearLayout.LayoutParams(-1,76));
        updateUi();
    }
    LinearLayout metric(String title,int color){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(8,4,8,4);TextView t=label(title);t.setTextColor(MUTED);x.addView(t);return x;}
    void buyButton(LinearLayout row,boolean yes){Button b=actionBtn(yes?"BUY YES":"BUY NO",yes?Color.rgb(20,150,70):Color.rgb(185,43,50));b.setOnClickListener(v->paperOrder(yes));row.addView(b,new LinearLayout.LayoutParams(0,72,1));}
    void adjustAmount(double d){double a=amount()+d;if(a<1)a=1;orderAmount.setText(df.format(a).replace(",",""));updateUi();}
    void refreshBtcFallback(){api.fetchSpotBtc((p,e)->{if(p>0){btc=p;updateUi();}});}
    void connectFeed(){
        api.connectMarketFeed(selected.yesToken,selected.noToken,t->{
            if("CONNECTED".equals(t.eventType)){feedLive=true;status.setText("● PAPER  •  CLOB LIVE");}
            else if("ERROR".equals(t.eventType)){feedLive=false;status.setText("● PAPER  •  FEED ERROR");}
            else if("DISCONNECTED".equals(t.eventType)){feedLive=false;status.setText("● PAPER  •  DISCONNECTED");}
            if(selected!=null){
                if(selected.yesToken.equals(t.assetId)){updateTokenState(t,true);} else if(selected.noToken!=null&&selected.noToken.equals(t.assetId)){updateTokenState(t,false);}
            }
            updateUi();
        });
    }
    void capturePriceToBeatIfAvailable(){
        if(selected==null||selected.targetPrice>0)return;
        double chain=api.getLatestChainlinkPrice(); long ts=api.getLatestChainlinkTimeMs();
        if(chain>0 && ts>=selected.startMs && ts<=selected.startMs+3000){selected.targetPrice=chain;updateUi();return;}
        if(selected.slug!=null&&!selected.slug.isEmpty()){
            long now=System.currentTimeMillis();
            if(now-lastTargetFetchMs<5000)return;
            lastTargetFetchMs=now;
            final PolymarketApi.Market market=selected;
            api.fetchPriceToBeatFromEventPage(market.slug,(ok,msg)->{
                if(ok&&msg.startsWith("PRICE_TO_BEAT ")){double v=parseDouble(msg.substring("PRICE_TO_BEAT ".length()));if(v>0&&selected==market){market.targetPrice=v;updateUi();}}
            });
        }
    }
    void updateTokenState(PolymarketApi.FeedTick t,boolean yes){
        if(t.bestBid!=null&&!t.bestBid.isEmpty()){double x=parseDouble(t.bestBid);if(x>0){if(yes)yesBid=x;else noBid=x;}}
        if(t.bestAsk!=null&&!t.bestAsk.isEmpty()){double x=parseDouble(t.bestAsk);if(x>0){if(yes)yesAsk=x;else noAsk=x;}}
        // IMPORTANT: price_change.price is the affected order-book level, not the
        // current market probability. Only last_trade_price may be used as a trade.
        if("last_trade_price".equals(t.eventType)){
            double trade=parseDouble(t.lastPrice); if(trade>0){if(yes)currentYes=trade;else currentNo=trade;}
        }
        double bid=yes?yesBid:noBid, ask=yes?yesAsk:noAsk;
        double p=(bid>0&&ask>0)?(bid+ask)/2.0:(ask>0?ask:(bid>0?bid:-1));
        if(p>0){if(yes)currentYes=p;else currentNo=p;}
        if(currentYes>0 && currentNo<=0) currentNo=1-currentYes;
        if(currentNo>0 && currentYes<=0) currentYes=1-currentNo;
    }
    void sampleState(){capturePriceToBeatIfAvailable();resolvePaperTradesIfNeeded();double probability=strategyProbability();if(probability<=0)return;double prev=hist.peekLast()==null?-1:hist.peekLast();if(prev>0&&probability>prev+.01&&dropped)jumps++;if(probability<=dropMax&&probability>=dropMin)dropped=true;if(dropped&&probability>=reversal)reversed=true;hist.addLast(probability);while(hist.size()>90)hist.removeFirst();saveActiveStrategy();lastSample=System.currentTimeMillis();checkAutomation();updateUi();}
    void updateUi(){
        if(marketTitle==null)return;
        if(uiUpdatePending)return;
        uiUpdatePending=true;
        if(uiDispatch==null) uiDispatch=()->{uiUpdatePending=false;renderUi();};
        timer.postDelayed(uiDispatch,120);
    }
    private void renderUi(){
        if(marketTitle==null)return;
        if(selected!=null)marketTitle.setText(selected.question);
        if(btcText!=null)btcText.setText("BTC SPOT  "+(btc>0?money(btc):"—"));
        if(targetText!=null){double target=selected==null?-1:selected.targetPrice;targetText.setText(target>0?"PRICE NEEDED  $"+money(target)+"\nΔ "+(btc>0?money(target-btc):"—"):"PRICE NEEDED  —\nwaiting for Chainlink open");}
        if(timeLeftText!=null)timeLeftText.setText(selected==null?"No market selected — scanner running automatically": "5-MIN MARKET  •  "+formatRemaining());
        if(yesText!=null)yesText.setText(currentYes>0?pct(currentYes):"—"); if(noText!=null)noText.setText(currentNo>0?pct(currentNo):"—");
        if(yesBookText!=null)yesBookText.setText("Bid / Ask  "+(yesBid>0?money(yesBid*100)+"¢":"—")+" / "+(yesAsk>0?money(yesAsk*100)+"¢":"—"));
        if(noBookText!=null)noBookText.setText("Bid / Ask  "+(noBid>0?money(noBid*100)+"¢":"—")+" / "+(noAsk>0?money(noAsk*100)+"¢":"—"));
        if(feedState!=null)feedState.setText(feedLive?"● CLOB LIVE  •  YES/NO BOOK STREAMING":"○ CLOB OFFLINE"); if(status!=null)status.setText((automationEnabled?(automationLiveRequested?"● AUTO LIVE":"● AUTO PAPER"):(liveTradingArmed?"● LIVE READY":"● PAPER"))+"  •  "+(feedLive?"CLOB LIVE":"DISCONNECTED"));
        if(reqText!=null)reqText.setText(requirements()); if(signalText!=null)signalText.setText("SIGNAL  •  "+signal()); if(feedLog!=null)feedLog.setText(feedLines()); if(orderInfo!=null)orderInfo.setText(orderPreview());
    }
    boolean isFiveMinuteSelected(){if(selected==null||selected.startMs<=0||selected.endMs<=selected.startMs)return false; long d=selected.endMs-selected.startMs; return d>=4*60*1000L&&d<=6*60*1000L;}
    long remainingSeconds(){return selected==null?-1:(selected.endMs-System.currentTimeMillis())/1000;}
    String formatRemaining(){long s=remainingSeconds();if(s<0)return "ended";return String.format(Locale.US,"%02d:%02d remaining",s/60,s%60);}
    String requirements(){long sec=remainingSeconds();boolean tw=sec>=windowMin&&sec<=windowMax;double probability=strategyProbability();String side="NO".equals(strategySide)?"NO":"YES";return "STRATEGY: "+side+" probability\n"+(dropped?"✓":"○")+" Drop to "+(int)(dropMax*100)+"–"+(int)(dropMin*100)+"%\n"+(reversed?"✓":"○")+" Reversal above "+(int)(reversal*100)+"%\n"+(jumps>=requiredJumps?"✓":"○")+" "+requiredJumps+" quick jumps\n"+(probability>=confirmation?"✓":"○")+" Confirmation ≥"+(int)(confirmation*100)+"%\n"+(tw?"✓":"○")+" Time window "+windowMax+"–"+windowMin+" sec  ("+(sec>=0?sec+"s":"—")+")";}
    String signal(){long sec=remainingSeconds();double probability=strategyProbability();return dropped&&reversed&&jumps>=requiredJumps&&probability>=confirmation&&selected!=null&&sec>=windowMin&&sec<=windowMax?"READY — "+strategySide:"WAITING / NOT ALL MET";}
    String feedLines(){if(hist.isEmpty())return "Waiting for CLOB book / price events…\n2-second state sampler: active";StringBuilder s=new StringBuilder();int n=0;for(Double p:hist){if(n++>0)s.append("\n");s.append(pct(p));if(n>=8)break;}return strategySide+" history (2s):\n"+s.toString();}
    String orderPreview(){double amt=amount();double yp=currentYes>0?currentYes:(yesAsk>0?yesAsk:0);double np=currentNo>0?currentNo:(noAsk>0?noAsk:0);return "YES  "+(yp>0?pct(yp):"—")+"   NO  "+(np>0?pct(np):"—")+"\nStrategy  "+strategySide+"   Amount  $"+money(amt)+"\nEst. "+strategySide+" shares  "+df.format(("NO".equals(strategySide)?np:yp)>0?amt/("NO".equals(strategySide)?np:yp):0)+"\nSignal  "+signal();}
    double amount(){try{return Double.parseDouble(orderAmount.getText().toString());}catch(Exception e){return 100;}}
    void paperOrder(boolean yes){double amt=amount();double p=yes?(yesAsk>0?yesAsk:currentYes):(noAsk>0?noAsk:currentNo);if(p<=0){Toast.makeText(this,"No live CLOB price yet",Toast.LENGTH_SHORT).show();return;}if(liveTradingArmed){new AlertDialog.Builder(this).setTitle((yes?"BUY YES":"BUY NO")+" — LIVE").setMessage("REAL ORDER\n\nPrice estimate: "+pct(p)+"\nAmount: $"+money(amt)+"\nEstimated shares: "+df.format(amt/p)+"\n\nThis submits a FOK market order at the current book. Continue?").setPositiveButton("SUBMIT LIVE",(d,w)->executeLiveOrder(yes,amt,false)).setNegativeButton("CANCEL",null).show();return;}new AlertDialog.Builder(this).setTitle((yes?"BUY YES":"BUY NO")+" — PAPER").setMessage("Price: "+pct(p)+"\nAmount: $"+money(amt)+"\nEstimated shares: "+df.format(amt/p)+"\n\n"+signal()).setPositiveButton("CONFIRM",(d,w)->recordPaperTrade(yes,amt,p,false)).setNegativeButton("CANCEL",null).show();}

    void recordPaperTrade(boolean yes,double amt,double price,boolean automatic){
        if(selected==null||price<=0||amt<=0)return;
        PaperTrade t=new PaperTrade();t.id=UUID.randomUUID().toString();t.marketId=selected.id;t.question=selected.question;t.side=yes?"YES":"NO";t.amount=amt;t.entryPrice=price;t.shares=amt/price;t.createdMs=System.currentTimeMillis();t.closeMs=selected.endMs;
        paperTrades.add(t);savePaperTrades();
        automationStatus=automatic?"AUTO PAPER ORDER RECORDED — BUY "+t.side+" $"+money(amt)+" @ "+pct(price):automationStatus;
        Toast.makeText(this,(automatic?"Automatic ":"")+"paper order recorded",Toast.LENGTH_SHORT).show();updateUi();
    }
    void resolvePaperTradesIfNeeded(){
        long now=System.currentTimeMillis();if(now-lastPaperResolutionCheckMs<10000)return;lastPaperResolutionCheckMs=now;
        for(PaperTrade t:paperTrades){if(t.resolved||t.closeMs<=0||now<t.closeMs+2000)continue;
            api.fetchMarketResolution(t.marketId,(resolved,winningSide,yesResult,noResult,error)->{
                if(!resolved)return;
                for(PaperTrade x:paperTrades){if(!x.id.equals(t.id)||x.resolved)continue;double result="YES".equals(x.side)?yesResult:noResult; x.resultPrice=result; x.won=result>=0.999; x.pnl=x.won?(x.shares-x.amount):-x.amount; x.resolved=true;}
                savePaperTrades();updateUi();if(performanceVisible)runOnUiThread(this::showPerformance);
            });
        }
    }
    String paperStats(){int total=paperTrades.size(),closed=0,wins=0;double staked=0,pnl=0,openValue=0;for(PaperTrade t:paperTrades){staked+=t.amount;if(t.resolved){closed++;if(t.won)wins++;pnl+=t.pnl;}else{double live="YES".equals(t.side)?currentYes:currentNo;if(live>0)openValue+=t.shares*live;}}double winRate=closed>0?100.0*wins/closed:0;double roi=staked>0?100.0*pnl/staked:0;return "PAPER TRADES  "+total+"\nCLOSED  "+closed+"   OPEN  "+(total-closed)+"\nWINS  "+wins+"   WIN RATE  "+df.format(winRate)+"%\nNET P/L  $"+money(pnl)+"   ROI  "+df.format(roi)+"%\nOPEN MARKET VALUE  $"+money(openValue);}
    String paperTradeLine(PaperTrade t){String state=t.resolved?(t.won?"WIN":"LOSS"):"OPEN";String result=t.resolved?(" P/L $"+money(t.pnl)):"  now $"+money(t.shares*("YES".equals(t.side)?Math.max(0,currentYes):Math.max(0,currentNo)));return state+"  "+t.side+"  $"+money(t.amount)+" @ "+pct(t.entryPrice)+result;}

    boolean allRequirementsMet(){long sec=remainingSeconds();return selected!=null&&strategyProbability()>0&&dropped&&reversed&&jumps>=requiredJumps&&strategyProbability()>=confirmation&&sec>=windowMin&&sec<=windowMax;}
    void checkAutomation(){
        if(!automationEnabled){automationStatus="OFF";return;}
        if(!allRequirementsMet()){automationStatus="ARMED — WAITING FOR REQUIREMENTS";return;}
        long now=System.currentTimeMillis();
        if(now-lastAutoOrderMs < automationCooldownSec*1000L){automationStatus="COOLDOWN — "+((automationCooldownSec*1000L-(now-lastAutoOrderMs)+999)/1000)+"s";return;}
        if(selected!=null && selected.id.equals(lastAutoMarketId)){automationStatus="ONE ORDER ALREADY FIRED FOR THIS MARKET";return;}
        boolean yes="ACTIVE".equals(automationSide)?"YES".equals(strategySide):automationSide.equals("YES");
        double p=yes?currentYes:currentNo;
        if(p<=0){automationStatus="ARMED — WAITING FOR PRICE";return;}
        if(automationLiveRequested){
            if(!liveTradingArmed || !auth.hasPrivateKey() || !auth.hasCreds()){automationStatus="LIVE ARMED — WAITING FOR AUTH / SESSION ENABLE";return;}
            lastAutoOrderMs=now; lastAutoMarketId=selected.id;
            double amt=automationAmount; automationStatus="LIVE ORDER SUBMITTING — BUY "+(yes?"YES":"NO")+" $"+money(amt);
            executeLiveOrder(yes,amt,true);
            return;
        }
        lastAutoOrderMs=now; lastAutoMarketId=selected.id;
        double amt=automationAmount;
        automationStatus="AUTO PAPER ORDER FIRED — BUY "+(yes?"YES":"NO")+" $"+money(amt)+" @ "+pct(p);
        recordPaperTrade(yes,amt,p,true);
        savePrefs();
    }
    void executeLiveOrder(boolean yes,double amt,boolean automatic){
        if(selected==null){Toast.makeText(this,"No selected market",Toast.LENGTH_SHORT).show();return;}
        if(!liveTradingArmed||!auth.hasPrivateKey()||!auth.hasCreds()){Toast.makeText(this,"Live execution requires session arming and authenticated CLOB keys",Toast.LENGTH_LONG).show();return;}
        String token=yes?selected.yesToken:selected.noToken;
        api.fetchOrderBook(token,(book,error)->{
            if(!error.isEmpty()||book==null){if(automatic)automationStatus="LIVE ORDER BLOCKED — BOOK ERROR: "+error;Toast.makeText(this,"Live order blocked: "+error,Toast.LENGTH_LONG).show();return;}
            double total=0,worst=0;
            for(double[] level:book.asks){if(level[0]<=0||level[1]<=0)continue;double take=Math.min(level[1],Math.max(0,amt-total)/level[0]);total+=take*level[0];worst=level[0];if(total+1e-9>=amt)break;}
            if(total+1e-9<amt||worst<=0){if(automatic)automationStatus="LIVE ORDER BLOCKED — INSUFFICIENT FOK LIQUIDITY";Toast.makeText(this,"FOK blocked: insufficient ask liquidity for $"+money(amt),Toast.LENGTH_LONG).show();return;}
            final double finalWorst=worst;
            double estShares=amt/finalWorst;
            if(estShares+1e-9<book.minOrderSize){if(automatic)automationStatus="LIVE ORDER BLOCKED — BELOW MINIMUM ORDER SIZE";Toast.makeText(this,"Below market minimum order size",Toast.LENGTH_LONG).show();return;}
            if(automatic)automationStatus="LIVE ORDER SUBMITTING @ "+pct(finalWorst);
            auth.placeLiveMarketBuy(token,amt,finalWorst,book.tickSize,book.negRisk,(ok,msg)->runOnUiThread(()->{
                if(ok){automationStatus=(automatic?"LIVE AUTO ORDER SENT — ":"LIVE ORDER SENT — ")+ (yes?"BUY YES":"BUY NO")+" $"+money(amt)+" @ "+pct(finalWorst);}
                else if(automatic){automationStatus="LIVE ORDER FAILED";lastAutoOrderMs=0;lastAutoMarketId="";}
                Toast.makeText(this,msg,Toast.LENGTH_LONG).show();updateUi();
            }));
        });
    }

    void showAutomation(){
        clear(); section("AUTOMATION ENGINE"); LinearLayout c=box();
        boolean live=automationLiveRequested;
        c.addView(value(automationEnabled?(live?"● LIVE AUTOMATION ARMED":"● PAPER AUTOMATION ARMED"):"○ AUTOMATION OFF",18,automationEnabled?(live?BAD:GOOD):MUTED));
        c.addView(tv("Automation evaluates the live strategy every 2 seconds. Live mode submits a V2 FOK market BUY only after all requirements are met and the current order book has enough liquidity for the configured amount.",14));
        Spinner side=new Spinner(this); ArrayAdapter<String> sa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"ACTIVE STRATEGY","YES","NO"}); side.setAdapter(sa); side.setSelection("ACTIVE".equals(automationSide)?0:(automationSide.equals("NO")?2:1)); c.addView(label("Automatic side (or ACTIVE STRATEGY)")); c.addView(side,new LinearLayout.LayoutParams(-1,52));
        EditText amt=field("Automatic order amount USD",money(automationAmount),false);amt.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);c.addView(amt);
        EditText cool=field("Cooldown seconds",String.valueOf(automationCooldownSec),false);cool.setInputType(InputType.TYPE_CLASS_NUMBER);c.addView(cool);
        TextView req=tv("Trigger: ALL requirements MET + live CLOB + inside time window.\n\nCurrent: "+requirements()+"\n\nSession live execution: "+(liveTradingArmed?"ENABLED":"OFF")+"\nCLOB auth: "+(auth.hasCreds()?"OK":"MISSING")+"\nPrivate signer: "+(auth.hasPrivateKey()?"ENCRYPTED ON DEVICE":"MISSING"),14);c.addView(req);
        TextView st=value("STATUS  •  "+automationStatus,15,automationEnabled?GOOD:MUTED);c.addView(st);
        Button toggle=actionBtn(automationEnabled?"DISARM AUTOMATION":"ARM PAPER AUTOMATION",automationEnabled?Color.rgb(120,55,55):BLUE);
        toggle.setOnClickListener(v->{try{String selectedAutomationSide=side.getSelectedItem().toString(); automationSide="ACTIVE STRATEGY".equals(selectedAutomationSide)?"ACTIVE":selectedAutomationSide;automationAmount=Double.parseDouble(amt.getText().toString());automationCooldownSec=Long.parseLong(cool.getText().toString());if(automationAmount<=0||automationCooldownSec<0)throw new Exception();}catch(Exception e){Toast.makeText(this,"Invalid automation settings",Toast.LENGTH_SHORT).show();return;}automationEnabled=!automationEnabled;automationLiveRequested=false;automationStatus=automationEnabled?"ARMED — WAITING FOR REQUIREMENTS":"OFF";savePrefs();showAutomation();});c.addView(toggle,new LinearLayout.LayoutParams(-1,68));
        Button liveBtn=actionBtn(live?"DISARM LIVE AUTOMATION":"ARM LIVE AUTOMATION",live?Color.rgb(150,50,50):Color.rgb(190,95,30));
        liveBtn.setOnClickListener(v->{if(live){automationLiveRequested=false;automationStatus=automationEnabled?"ARMED — PAPER / WAITING FOR REQUIREMENTS":"OFF";showAutomation();return;}if(!liveTradingArmed||!auth.hasCreds()||!auth.hasPrivateKey()){new AlertDialog.Builder(this).setTitle("Live automation unavailable").setMessage("First authenticate the CLOB in CLOB KEYS, then use ENABLE LIVE EXECUTION (SESSION). Your signer private key stays encrypted on this device.").setPositiveButton("OPEN CLOB KEYS",(d,w)->showClobKeys()).setNegativeButton("CANCEL",null).show();return;}new AlertDialog.Builder(this).setTitle("ARM REAL-MONEY AUTOMATION").setMessage("This will allow the strategy engine to submit real FOK BUY orders automatically when every configured requirement is MET.\n\nMaximum per order: $"+money(automationAmount)+"\nSide: "+("ACTIVE".equals(automationSide)?strategySide:automationSide)+"\nCooldown: "+automationCooldownSec+"s\n\nDo you understand that this can lose money and that orders are irreversible once matched?").setPositiveButton("ARM LIVE",(d,w)->{automationEnabled=true;automationLiveRequested=true;automationStatus="LIVE ARMED — WAITING FOR REQUIREMENTS";savePrefs();showAutomation();}).setNegativeButton("CANCEL",null).show();});
        c.addView(liveBtn,new LinearLayout.LayoutParams(-1,68));
        Button dash=actionBtn("OPEN LIVE ENGINE",PANEL2);dash.setOnClickListener(v->showDashboard());c.addView(dash,new LinearLayout.LayoutParams(-1,64));
    }

    void resetStrategyState(){yesHist.clear();noHist.clear();hist.clear();yesDropped=noDropped=false;yesReversed=noReversed=false;yesJumps=noJumps=0;dropped=reversed=false;jumps=0;savePrefs();}
    double strategyProbability(){return "NO".equals(strategySide)?currentNo:currentYes;}

    void scanAndSelect(){
        if(scannerStatus!=null)scannerStatus.setText("Scanning Gamma for the nearest active 5-minute BTC market (any time remaining)…");
        api.fetchFiveMinuteBtcMarket((ms,e)->{
            if(!e.isEmpty()){if(scannerStatus!=null)scannerStatus.setText(e);if(selected==null)updateUi();return;}
            PolymarketApi.Market best=ms.get(0); if(selected!=null && selected.id.equals(best.id) && remainingSeconds()>0 && feedLive)return;
            if(feedLive)api.disconnectMarketFeed(); feedLive=false; selected=best;lastTargetFetchMs=0;capturePriceToBeatIfAvailable();resetStrategyState();currentYes=currentNo=yesBid=yesAsk=noBid=noAsk=-1;refreshBtcFallback();showDashboard();connectFeed();
        });
    }
    void showScanner(){clear();section("BTC MARKET SCANNER  •  5-MIN ONLY");scannerStatus=tv("Scanning for active 5-minute BTC markets…",14);content.addView(scannerStatus);Button scan=actionBtn("SCAN NOW",BLUE);scan.setOnClickListener(v->scanAndSelect());content.addView(scan,new LinearLayout.LayoutParams(-1,64));api.fetchFiveMinuteBtcMarket((ms,e)->{if(!e.isEmpty()){scannerStatus.setText(e);return;}scannerStatus.setText("Found "+ms.size()+" active 5-minute candidate(s), nearest close first.");for(PolymarketApi.Market m:ms){LinearLayout c=box();c.addView(value(m.question,16,TEXT));c.addView(label("Duration: "+duration(m)+"  •  closes in "+remainingFor(m)+"  •  target: "+(m.targetPrice>0?"$"+money(m.targetPrice):"not detected")));Button b=actionBtn("SELECT + CONNECT",BLUE);b.setOnClickListener(v->{if(feedLive)api.disconnectMarketFeed();feedLive=false;selected=m;lastTargetFetchMs=0;resetStrategyState();currentYes=currentNo=yesBid=yesAsk=noBid=noAsk=-1;capturePriceToBeatIfAvailable();showDashboard();connectFeed();});c.addView(b,new LinearLayout.LayoutParams(-1,62));}});}
    String duration(PolymarketApi.Market m){if(m.startMs<=0||m.endMs<=m.startMs)return "unknown";return ((m.endMs-m.startMs)/60000)+" min";} String remainingFor(PolymarketApi.Market m){long s=(m.endMs-System.currentTimeMillis())/1000;return s<0?"ended":String.format(Locale.US,"%02d:%02d",s/60,s%60);}

    EditText field(String hint,String value,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(14);e.setSingleLine(true);e.setPadding(10,8,10,8);if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    void showStrategy(){
        clear();section("STRATEGY ALTERATION  •  "+strategySide);
        LinearLayout switchBox=box();
        switchBox.addView(label("Choose which probability stream drives the strategy engine. YES and NO keep independent parameters and state."));
        LinearLayout sw=row();
        Button yes=actionBtn("YES STRATEGY", "YES".equals(strategySide)?GOOD:PANEL2);
        Button no=actionBtn("NO STRATEGY", "NO".equals(strategySide)?BAD:PANEL2);
        yes.setOnClickListener(v->switchStrategy("YES")); no.setOnClickListener(v->switchStrategy("NO"));
        sw.addView(yes,new LinearLayout.LayoutParams(0,64,1));sw.addView(no,new LinearLayout.LayoutParams(0,64,1));switchBox.addView(sw);
        switchBox.addView(value("ACTIVE: "+strategySide+" probability",16,"NO".equals(strategySide)?BAD:GOOD));
        LinearLayout c=box();c.addView(label("These parameters apply only to the active "+strategySide+" strategy and are stored separately from the other side."));
        EditText dmax=field("Drop upper %",String.valueOf((int)(dropMax*100)),false);EditText dmin=field("Drop lower %",String.valueOf((int)(dropMin*100)),false);EditText rev=field("Reversal %",String.valueOf((int)(reversal*100)),false);EditText conf=field("Confirmation %",String.valueOf((int)(confirmation*100)),false);EditText jumpsE=field("Required quick jumps",String.valueOf(requiredJumps),false);EditText wmax=field("Window start seconds",String.valueOf(windowMax),false);EditText wmin=field("Window end seconds",String.valueOf(windowMin),false);
        for(EditText e:new EditText[]{dmax,dmin,rev,conf,jumpsE,wmax,wmin}){e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);c.addView(e);}
        Button save=actionBtn("SAVE "+strategySide+" STRATEGY PARAMETERS",BLUE);save.setOnClickListener(v->{try{dropMax=Double.parseDouble(dmax.getText().toString())/100;dropMin=Double.parseDouble(dmin.getText().toString())/100;reversal=Double.parseDouble(rev.getText().toString())/100;confirmation=Double.parseDouble(conf.getText().toString())/100;requiredJumps=Integer.parseInt(jumpsE.getText().toString());windowMax=Long.parseLong(wmax.getText().toString());windowMin=Long.parseLong(wmin.getText().toString());if(dropMin>dropMax||reversal<0||reversal>1||confirmation<0||confirmation>1||requiredJumps<1||windowMin<0||windowMax<windowMin)throw new Exception();savePrefs();Toast.makeText(this,strategySide+" strategy saved",Toast.LENGTH_SHORT).show();showDashboard();}catch(Exception ex){Toast.makeText(this,"Invalid parameter",Toast.LENGTH_SHORT).show();}});
        c.addView(save,new LinearLayout.LayoutParams(-1,68));
        Button dash=actionBtn("OPEN LIVE ENGINE  •  ACTIVE "+strategySide,PANEL2);dash.setOnClickListener(v->showDashboard());c.addView(dash,new LinearLayout.LayoutParams(-1,64));
    }
    void showExecution(){clear();section("ORDERS / EXECUTION");LinearLayout c=box();c.addView(value(liveTradingArmed?"LIVE EXECUTION ENABLED":"PAPER MODE",18,liveTradingArmed?BAD:ORANGE));c.addView(tv("Manual YES/NO buttons use the live CLOB book. With session live execution enabled, the confirmation dialog submits a V2 FOK market BUY using the current ask-side liquidity. Automation has its own explicit live arming control.",14));Button live=actionBtn("OPEN LIVE ENGINE",BLUE);live.setOnClickListener(v->showDashboard());c.addView(live,new LinearLayout.LayoutParams(-1,68));}
    void showClobKeys(){clear();section("CLOB KEYS / AUTHENTICATION");LinearLayout c=box();c.addView(label("Private key and L2 credentials are encrypted with Android Keystore. Never put them in GitHub or Codemagic."));EditText pk=field("Wallet private key (L1 auth)","",true);EditText addr=field("Wallet / signer address",auth.getAddress(),false);EditText key=field("CLOB API key",auth.getApiKey(),false);EditText secret=field("CLOB secret (blank = keep existing)","",true);EditText pass=field("CLOB passphrase (blank = keep existing)","",true);c.addView(pk);c.addView(addr);c.addView(key);c.addView(secret);c.addView(pass);Button authBtn=actionBtn("AUTHENTICATE / REFRESH CLOB KEYS",BLUE);authBtn.setOnClickListener(v->{String p=pk.getText().toString().trim();if(p.isEmpty()){Toast.makeText(this,"Enter wallet private key for L1 authentication",Toast.LENGTH_LONG).show();return;}auth.authenticate(p,0,(ok,msg)->runOnUiThread(()->Toast.makeText(this,msg,Toast.LENGTH_LONG).show()));});c.addView(authBtn,new LinearLayout.LayoutParams(-1,68));Button save=btn("SAVE EXISTING L2 CREDENTIALS");save.setOnClickListener(v->{auth.updateManualCredentials(addr.getText().toString(),key.getText().toString(),secret.getText().toString(),pass.getText().toString());Toast.makeText(this,"Saved locally",Toast.LENGTH_SHORT).show();});c.addView(save,new LinearLayout.LayoutParams(-1,58));Button test=btn("TEST AUTHENTICATED CLOB READ");test.setOnClickListener(v->auth.testAuthenticated((ok,msg)->runOnUiThread(()->Toast.makeText(this,msg,Toast.LENGTH_LONG).show())));c.addView(test,new LinearLayout.LayoutParams(-1,58));Button arm=actionBtn(liveTradingArmed?"DISABLE LIVE EXECUTION (SESSION)":"ENABLE LIVE EXECUTION (SESSION)",liveTradingArmed?Color.rgb(150,50,50):Color.rgb(190,95,30));arm.setOnClickListener(v->{if(liveTradingArmed){liveTradingArmed=false;automationLiveRequested=false;automationEnabled=false;automationStatus="OFF";Toast.makeText(this,"Live execution disabled",Toast.LENGTH_SHORT).show();showClobKeys();return;}if(!auth.hasCreds()||!auth.hasPrivateKey()){Toast.makeText(this,"Authenticate CLOB first",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle("Enable real-money execution").setMessage("Manual BUY YES/NO will submit real FOK market orders after confirmation. Live automation can also be armed separately. Continue?").setPositiveButton("ENABLE",(d,w)->{liveTradingArmed=true;showClobKeys();}).setNegativeButton("CANCEL",null).show();});c.addView(arm,new LinearLayout.LayoutParams(-1,68));}
    void showPerformance(){
        clear();performanceVisible=true;section("PERFORMANCE  •  PAPER TRADING");LinearLayout c=box();c.addView(value(paperStats(),16,TEXT));
        Button refresh=actionBtn("REFRESH / SETTLE CLOSED MARKETS",BLUE);refresh.setOnClickListener(v->{lastPaperResolutionCheckMs=0;resolvePaperTradesIfNeeded();showPerformance();});c.addView(refresh,new LinearLayout.LayoutParams(-1,64));
        Button clear=btn("CLEAR PAPER HISTORY");clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear paper history?").setMessage("This removes all locally stored paper trades and performance results. Live orders are not affected.").setPositiveButton("CLEAR",(d,w)->{paperTrades.clear();savePaperTrades();showPerformance();}).setNegativeButton("CANCEL",null).show());c.addView(clear,new LinearLayout.LayoutParams(-1,58));
        LinearLayout list=box();list.addView(label("TRADE HISTORY  •  newest first"));if(paperTrades.isEmpty())list.addView(tv("No paper trades yet. Confirm BUY YES/BUY NO in PAPER mode or arm PAPER AUTOMATION.",14));else{for(int i=paperTrades.size()-1;i>=0;i--){PaperTrade t=paperTrades.get(i);TextView line=tv(paperTradeLine(t)+"\n"+t.question,13);line.setPadding(8,10,8,10);list.addView(line);}}
    }
    double parseDouble(String s){try{return Double.parseDouble(s);}catch(Exception e){return -1;}}
    String pct(double x){return x<0?"—":df.format(x*100)+"%";} String money(double x){return new DecimalFormat("#,##0.00").format(x);}
    @Override protected void onDestroy(){timer.removeCallbacks(btcFallbackLoop);timer.removeCallbacks(stateLoop);timer.removeCallbacks(marketLoop);if(uiDispatch!=null)timer.removeCallbacks(uiDispatch);uiUpdatePending=false;api.disconnectMarketFeed();api.disconnectCryptoBtcFeed();super.onDestroy();}
}
