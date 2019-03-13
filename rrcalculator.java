
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

public class rrcalculator {
    private ArrayList<Float> smoothList;
    private ArrayList<Integer> peaks;
    private ArrayList<Float> aveList;
    private int firstIndexUpdate = 0;
    private double updatingrate = 0.02;
    private double min_height = 0.34;
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
            for (int i=0; i <= aveList.size(); i++){
                sum_smooth = aveList.get(i) + sum_smooth;
            }
        }
        else{
            aveList.remove(0);
            for (int i=0; i <= aveList.size(); i++){
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
        ArrayList<Integer> tail = new ArrayList<Integer>();
        
        while (i+width < smoothList.size() - 2){
            if (smoothList.get(i) > min_height && smoothList.get(i) >= smoothList.get(i-1)){
                width = 1;
                while (i + width < smoothList.size()-2 && smoothList.get(i) <= smoothList.get(i+width) && smoothList.get(i+width) <= smoothList.get(i+width+1)){
                    width += 1;
                }
                if (smoothList.get(i+width) > smoothList.get(i+width+1)){
                    tail.add(i+width); 
                    i += width+1;
                }
                else{
                    i += width;
                }
            }
            else{
                i++;
            }
        }
        return tail;
    }
    
    public double calculateRR(float smoothValue){
        smoothList.add(smoothValue);
        if (smoothList.size() > 100){
            smoothList.remove(0);
            firstIndexUpdate += 1;
        }
        
        for (int i=0; i < fPeaks(smoothList).size(); i++){
            if (!peaks.contains(fPeaks(smoothList).get(i))){
                peaks.add(fPeaks(smoothList).get(i) + firstIndexUpdate);
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
            RR = R / peaks.size();
            RR = RR * updatingrate;
        }
        else{
            for (int i = 0; i < 9; i++){
                R = R + peaks.get(i+1)-peaks.get(i);
            }
            RR = R/peaks.size();
            RR = RR*updatingrate;
        }
        return RR*60;
    }
                
}
