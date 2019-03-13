package mainactivity;

/**
 * Write a description of rrcalculator here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Collections;
import java.lang.Math;
public class rrcalculator {
    private ArrayList<Float> smoothList;
    private ArrayList<Integer> peaks;
    private ArrayList<Float> aveList;
    private int firstIndexUpdate = 0;
    private double updatingrate = 0.02;
    //private double min_height = 0.03;
    public rrcalculator(){
        smoothList = new ArrayList<Float>();
        peaks = new ArrayList<Integer>();
        aveList = new ArrayList<Float>();
    }
    
    public float CalculateSmooth(float xAcceleration, float yAcceleration, float zAcceleration){
        float smooth;
        aveList.add((xAcceleration+yAcceleration+zAcceleration)/3);
        float sum_smooth = 0;
        if (aveList.size() <= 100){
            for (int i=0; i < aveList.size(); i++){
                sum_smooth = aveList.get(i) + sum_smooth;
            }
        }
        else{
            aveList.remove(0);
            for (int i=0; i < aveList.size(); i++){
                sum_smooth = aveList.get(i) + sum_smooth;
            }
        }
        
        smooth = sum_smooth / aveList.size();
        return smooth;
    }
    
    public ArrayList<Integer> fPeaks(ArrayList<Float> smoothList){
        boolean peakFound = false;
        int peak = 0;
        int i = 1;
        int width = 0;
        double slope = 0;
        double tempsum = 0;
        ArrayList<Integer> tail = new ArrayList<Integer>();
        ArrayList<Integer> taill = new ArrayList<Integer>();
        double difference = 0;
        difference = Collections.max(smoothList) - Collections.min(smoothList);
        double average = 0;
        double summ = 0;
        double tempmax = 0;
        double median = 0;
        double maxx = Collections.max(smoothList);
        int horizontal_distance = Math.abs(smoothList.indexOf(Collections.min(smoothList)) - smoothList.indexOf(Collections.max(smoothList)));
        for (int m=0; m<= smoothList.size()-1;m++){
            summ += smoothList.get(m);
        }
        average = summ/smoothList.size();
        if (smoothList.size()>=2){
            median = (smoothList.get(smoothList.size()/2)+smoothList.get(smoothList.size()/2-1))/2;
        }
        if (difference > 0.0015 && difference < 0.003 && horizontal_distance >= 50){
        while (i+width < smoothList.size()-8){
            if (smoothList.get(i) >= smoothList.get(i-1)){
                width = 0;
                while (i+width < smoothList.size()-8 && smoothList.get(i) <= smoothList.get(i+width) && smoothList.get(i+width) <= smoothList.get(i+width+1)){
                    width += 1;
                }
                if (smoothList.get(i+width) > smoothList.get(i+width+1)){
                    if(i+width>7){
                        for (int j =0; j<=9;j++){
                            tempsum += smoothList.get(i+width-6+j)-smoothList.get(i+width-7+j);
                        }
                        slope = tempsum/8;
                        if(Math.abs(slope) <= 0.000025 ){
                            if(smoothList.get(i+width-1)>median && Math.abs(smoothList.get(i+width-1)-maxx)<=0.00014){
                                tail.add(i+width-1); 
                            }
                        }
                        
                    }
                    i += width+1;
                    slope = 0;
                    tempsum =0;
                }
                else{
                    i += width;
                }
            }
            else{
                i++;
            }
        }
        }
        if (tail.size()>1){
            for(int n=0; n<=tail.size()-1;n++){
                if(smoothList.get(tail.get(n))>tempmax){
                    tempmax = smoothList.get(tail.get(n));
                }
            }
        }
        for(int p=0; p<=tail.size()-1;p++){
            if(smoothList.get(tail.get(p)) == tempmax){
                taill.add(tail.get(p));
            }
        }
        return taill;
        
    }
    
    public int calculateRR(float smoothValue){
        double maxvalue = 0;
        if (smoothList.size() == 100){
            smoothList.remove(0);
            smoothList.add(smoothValue);
            firstIndexUpdate += 1;
        }
        else{
            smoothList.add(smoothValue);
        }
        
        for (int i=0; i < fPeaks(smoothList).size(); i++){
            if (!peaks.contains(fPeaks(smoothList).get(i))){
                
                if(peaks.size()<=1){
                    peaks.add(fPeaks(smoothList).get(i) + firstIndexUpdate);
                    maxvalue = smoothList.get(fPeaks(smoothList).get(i));
                }
                else{
                    if(fPeaks(smoothList).get(i)+firstIndexUpdate - peaks.get(peaks.size()-1) <= 100){
                        if(smoothList.get(fPeaks(smoothList).get(i)) > maxvalue){
                            peaks.remove(peaks.size()-1);
                            peaks.add(fPeaks(smoothList).get(i) + firstIndexUpdate);
                            maxvalue = smoothList.get(fPeaks(smoothList).get(i));
                        }
                    }
                    else{
                        peaks.add(fPeaks(smoothList).get(i) + firstIndexUpdate);
                        maxvalue = 0;
                    }
                    
                }
            }
        }
        
        while(peaks.size() > 10){
            peaks.remove(0);
        }
        
        float R = 0;
        double RR;
        if (peaks.size() < 10){
            for (int i=0; i < peaks.size()-1; i++){
                R = R + peaks.get(i+1) - peaks.get(i);
            }
            RR = R / (peaks.size()-1);
            RR = RR * updatingrate;
            
        }
        
        else{
            for (int i = 0; i < 9; i++){
                R = R + peaks.get(i+1)-peaks.get(i);
            }
            RR = R/(peaks.size()-1);
            RR = RR*updatingrate;
        }
        
        if (peaks.size() <=2){
            return 0;
        }
        else{
            return peaks.get(peaks.size()-2);
        }
        //return RR * 60;
    }
                
}
