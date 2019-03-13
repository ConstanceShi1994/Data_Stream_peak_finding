package mainactivity;

import mainactivity.rrcalculator;
import edu.duke.*;
import org.apache.commons.csv.*;
import java.util.ArrayList;
import mainactivity.rrcalculator;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Collections;
import java.awt.Graphics;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
/**
 * Write a description of calculations here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */

public class calculations {
    rrcalculator smoothCalculator = new rrcalculator();
    private ArrayList<Float> smoothList;
    private ArrayList<Integer> xPoints;
    
    public calculations(){
        smoothList = new ArrayList<Float>();
        xPoints = new ArrayList<Integer>();
    }
    
    public void main() throws Exception{
        FileResource fr = new FileResource("C:\\Users\\15304\\testRR\\standing_still_patch_below_left_pec_RR.csv");
        CSVParser parser = fr.getCSVParser();
        String csvFile = "C:\\Users\\15304\\testRR\\testing7.csv";
        FileWriter writer = new FileWriter(csvFile);
        for(CSVRecord record:parser){
            float xAcceleration = Float.parseFloat(record.get("Ax"));
            float yAcceleration = Float.parseFloat(record.get("Ay"));
            float zAcceleration = Float.parseFloat(record.get("Az"));
            
            float smoothValue = smoothCalculator.CalculateSmooth(xAcceleration,yAcceleration,zAcceleration);
            System.out.print(smoothValue + " ");
            int rr = smoothCalculator.calculateRR(smoothValue);
            System.out.println(rr + " ");
            //writer.append(Float.toString(smoothValue));
            writer.append(String.valueOf(rr));
            writer.append("\n");
        }
        
    }
    
}
