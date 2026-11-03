package com.parcelguard.offline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative offline reader for LCD/LED weight displays. No Android dependencies.
 *  Unknown/multiple numeric readings never become confirmed weights automatically.
 */
final class ScaleWeightParser {
    enum Unit { KG, G }
    private static final int MAX_GRAMS = 100_000; // trial safety bound, not scale capacity
    static final class Sample {
        final int grams;
        final String raw;
        Sample(int grams,String raw){this.grams=grams;this.raw=raw;}
        boolean valid(){return grams>0;}
    }
    static final class Stability {
        final int grams;
        final int goodReadings, totalReadings;
        Stability(int grams,int good,int total){
            this.grams=grams;goodReadings=good;totalReadings=total;
        }
        boolean stable(){return grams>0 && goodReadings>=2;}
    }
    private static final Pattern READING = Pattern.compile(
        "^\\s*([0-9]{1,5}(?:[.,][0-9]{1,3})?)\\s*(KG|KGS|G|GRAMS?)?\\s*$");

    static Sample parse(String raw,Unit assumed) {
        if(raw==null || assumed==null)return new Sample(-1,raw);
        String upper=raw.toUpperCase(Locale.ROOT).replace('，',',').replace('．','.');
        Unit explicit=null;
        if(Pattern.compile("(?<![A-Z])KGS?(?![A-Z])").matcher(upper).find())explicit=Unit.KG;
        else if(Pattern.compile("(?<![A-Z])GRAMS?(?![A-Z])").matcher(upper).find()
          || Pattern.compile("(?<![A-Z])G(?![A-Z])").matcher(upper).find())explicit=Unit.G;
        List<Integer> values=new ArrayList<>();
        for(String line:upper.split("\\r?\\n")) {
            Matcher m=READING.matcher(line);
            if(!m.matches())continue;
            String number=m.group(1);
            String unit=m.group(2);
            Unit thisUnit=unit==null?(explicit==null?assumed:explicit)
                :(unit.startsWith("K")?Unit.KG:Unit.G);
            try {
                // Under gram selection, '1,250' conventionally means 1250 g.
                if(thisUnit==Unit.G && number.matches("[0-9]{1,3},[0-9]{3}"))
                    number=number.replace(",","");
                else number=number.replace(',','.');
                BigDecimal value=new BigDecimal(number);
                if(thisUnit==Unit.KG)value=value.multiply(new BigDecimal("1000"));
                int grams=value.setScale(0,RoundingMode.HALF_UP).intValueExact();
                if(grams>0 && grams<=MAX_GRAMS && !values.contains(grams))values.add(grams);
            }catch(ArithmeticException|NumberFormatException ignored){}
        }
        return values.size()==1?new Sample(values.get(0),raw):new Sample(-1,raw);
    }

    static Stability evaluate(List<Sample> samples) {
        if(samples==null || samples.isEmpty())return new Stability(-1,0,0);
        List<Integer> good=new ArrayList<>();
        for(Sample s:samples)if(s!=null && s.valid())good.add(s.grams);
        if(good.size()<2)return new Stability(-1,good.size(),samples.size());
        Collections.sort(good);
        int closestCount=0,estimate=-1;
        // Require at least two scans within 2 g (or 0.5% at high weight).
        for(int i=0;i<good.size();i++){
            int count=0;
            int tolerance=Math.max(2,(int)Math.round(good.get(i)*0.005));
            for(int g:good)if(Math.abs(g-good.get(i))<=tolerance)count++;
            if(count>closestCount){closestCount=count;estimate=good.get(i);}
        }
        return new Stability(closestCount>=2?estimate:-1,closestCount,samples.size());
    }
    private ScaleWeightParser(){}
}
