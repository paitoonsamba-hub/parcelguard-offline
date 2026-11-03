package com.parcelguard.offline;

import java.util.Arrays;
import java.util.List;
public final class ScaleWeightParserSmokeTest {
    private static int tested=0;
    static void check(String ocr,ScaleWeightParser.Unit assumed,int grams){
        int actual=ScaleWeightParser.parse(ocr,assumed).grams;
        if(actual!=grams)throw new AssertionError("OCR ["+ocr.replace("\n"," / ")+"] expected "+grams+" got "+actual);
        tested++;
    }
    public static void main(String[] args){
        check("0.750 kg",ScaleWeightParser.Unit.KG,750);
        check("0.750\nkg",ScaleWeightParser.Unit.KG,750);
        check("750\ng",ScaleWeightParser.Unit.G,750);
        check("750",ScaleWeightParser.Unit.G,750);
        check("0.75",ScaleWeightParser.Unit.KG,750);
        check("0,750",ScaleWeightParser.Unit.KG,750);
        check("1,250 g",ScaleWeightParser.Unit.G,1250);
        check("1.250 kg",ScaleWeightParser.Unit.KG,1250);
        check("MAX 30kg\n0.425 kg",ScaleWeightParser.Unit.KG,425);
        check("0.000 kg",ScaleWeightParser.Unit.KG,-1);
        check("-0.100 kg",ScaleWeightParser.Unit.KG,-1);
        check("LEXPU0720309511",ScaleWeightParser.Unit.KG,-1);
        check("0.750\n0.850 kg",ScaleWeightParser.Unit.KG,-1);
        check("999 kg",ScaleWeightParser.Unit.KG,-1);
        ScaleWeightParser.Stability good=ScaleWeightParser.evaluate(Arrays.asList(
            ScaleWeightParser.parse("0.750 kg",ScaleWeightParser.Unit.KG),
            ScaleWeightParser.parse("0.751 kg",ScaleWeightParser.Unit.KG),
            ScaleWeightParser.parse("TH39139AU6229B",ScaleWeightParser.Unit.KG)));
        if(!good.stable()||good.goodReadings!=2)throw new AssertionError("stable OCR recognition failed");
        tested++;
        ScaleWeightParser.Stability bad=ScaleWeightParser.evaluate(Arrays.asList(
            ScaleWeightParser.parse("0.750",ScaleWeightParser.Unit.KG),
            ScaleWeightParser.parse("0.900",ScaleWeightParser.Unit.KG),
            ScaleWeightParser.parse("1.100",ScaleWeightParser.Unit.KG)));
        if(bad.stable())throw new AssertionError("unstable OCR weights incorrectly accepted");
        tested++;
        System.out.println("ScaleWeightParser tests passed: "+tested);
    }
}
