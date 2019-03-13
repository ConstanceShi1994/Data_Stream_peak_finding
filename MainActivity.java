package com.lannbox.rfduinotest;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.lannbox.rfduinotest.rrcalculator;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import android.os.Handler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.round;
import static java.nio.file.Files.size;

import java.util.Iterator;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback, AdapterView.OnItemSelectedListener {

    private double graphLastXValue = 0d;
    private int ECGLastIndex = 0;
    private final Handler mHandler = new Handler();

    /*GraphView graph;
    private LineGraphSeries<DataPoint> mSeriesA, mSeriesB, mSeriesC;
    ArrayList<DataPoint> redDataPointsBuffer = new ArrayList<DataPoint>();
    ArrayList<DataPoint> irDataPointsBuffer = new ArrayList<DataPoint>();
    ArrayList<DataPoint> greenDataPointsBuffer = new ArrayList<DataPoint>();*/

    //MPAndroidChart https://github.com/PhilJay/MPAndroidChart/wiki
    LineChart chart, chart2, chart3;
    List<Entry> entries;
    LineDataSet redDataSet, irDataSet, greenDataSet;
    LineDataSet xDataSet, yDataSet, zDataSet;

    LineDataSet processorTempDataSet, accelTempDataSet, pulseOxTempDataSet;
    LineDataSet hrDataSet, ecgFiltDataSet;
    LineDataSet ecgDataSet, hbDataSet;
    LineDataSet bpmDataSet;
    LineData lineData, lineData2, lineData3;

    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;
    final private static int STATE_DISCONNECTING = 5;
    boolean isBound = false;

    private int state;
    List<Integer> m_hrList;

    private boolean scanStarted;
    private boolean scanning;

    private boolean onDeviceLogging = false;

    private BluetoothAdapter bluetoothAdapter;
    ArrayList<BluetoothDevice> bluetoothDeviceList;
    int selectedDeviceIndex;
    //private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;

    //private Button enableBluetoothButton;
    //private TextView scanStatusText;
    private Button scanButton;
    //private TextView deviceInfoText;
    //private TextView connectionStatusText;
    private TextView heartRateText, accelText;
    private TextView temperatureText;
    private Button connectButton, resetButton;
    private EditData valueEdit;
    private Button sendZeroButton;
    private Button recordOnDeviceButton;
    private Button sendValueButton;
    private Spinner chartSelect, deviceList;
    private Button clearButton;
    private LinearLayout dataLayout;

    private File logFile;
    private File uiLogFile;
    int lastCounter = -1;
    int missedPacketCounter = 0;
    rrcalculator smoothCalculator;

    ArrayList<Float> smoothList;
    ArrayList<Integer> peaks;
    int time_recording = 0;

    LinkedList<Long> heartBeatBuffer;
    double lastHR_bpm = 75; //initialize to something normal!

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
                //rfduinoService.disconnect();
                Log.d("WiSPR", "bluetoothStateReceiver: onReceive: BluetoothAdapter.STATE_ON");
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
                Log.d("WiSPR", "bluetoothStateReceiver: onReceive: BluetoothAdapter.STATE_OFF");
            }
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            Log.d("WiSPR", "scanModeReceiver: onReceive: != BluetoothAdapter.SCAN_MODE_NONE");
            updateUi();
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (rfduinoService.connect(bluetoothDeviceList.get(selectedDeviceIndex).getAddress())) {
                    upgradeState(STATE_CONNECTING);
                    Log.d("WiSPR", "rfduinoServiceConnection: onServiceConnected");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("WiSPR", "rfduinoServiceConnection: onServiceDisconnected");
            //rfduinoService.disconnect();
            //rfduinoService.close();
            //rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                Log.d("WiSPR", "rfduinoReceiver: RFduinoService.ACTION_CONNECTED");
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
                Log.d("WiSPR", "rfduinoReceiver: RFduinoService.ACTION_DISCONNECTED");
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
                //Log.d("WiSPR", "rfduinoReceiver: RFduinoService.ACTION_DATA_AVAILABLE");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //graph = (GraphView) findViewById(R.id.graph);
        //initGraph(graph);

        heartBeatBuffer = new LinkedList<Long>();
        smoothCalculator = new rrcalculator();
        initChartData();

        chart = (LineChart) findViewById(R.id.chart);
        List<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
        lineData = new LineData(dataSets);
        initChart(chart, lineData);

        chart2 = (LineChart) findViewById(R.id.chart2);
        List<ILineDataSet> dataSets2 = new ArrayList<ILineDataSet>();
        lineData2 = new LineData(dataSets2);
        initChart(chart2, lineData2);

        chart3 = (LineChart) findViewById(R.id.chart3);
        List<ILineDataSet> dataSets3 = new ArrayList<ILineDataSet>();
        lineData3 = new LineData(dataSets3);
        initChart(chart3, lineData3);

        m_hrList = new ArrayList<Integer>();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDeviceList = new ArrayList<BluetoothDevice>();
        selectedDeviceIndex = 0;

        /*// Bluetooth
        enableBluetoothButton = (Button) findViewById(R.id.enableBluetooth);
        enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBluetoothButton.setEnabled(false);
                enableBluetoothButton.setText(
                        bluetoothAdapter.enable() ? "Enabling bluetooth..." : "Enable failed!");
            }
        });*/

        // Find Device
        //scanStatusText = (TextView) findViewById(R.id.scanStatus);

        scanButton = (Button) findViewById(R.id.scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanStarted = true;
                bluetoothAdapter.startLeScan(MainActivity.this);
                /*bluetoothAdapter.startLeScan(
                        new UUID[]{ RFduinoService.UUID_SERVICE },
                        MainActivity.this);*/
            }
        });

        // Device Info
        //deviceInfoText = (TextView) findViewById(R.id.deviceInfo);

        // Connect Device
        //connectionStatusText = (TextView) findViewById(R.id.connectionStatus);
        //heartRateText = (TextView) findViewById(R.id.heartRate);
        //temperatureText = (TextView) findViewById(R.id.temperature);

        heartRateText = (TextView) findViewById(R.id.hrText);
        accelText = (TextView) findViewById(R.id.accelText);

        connectButton = (Button) findViewById(R.id.connect);
        connectButton.setTag(1);
        connectButton.setText("CONNECT");
        connectButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int status = (Integer) v.getTag();

                //If we are connecting
                if(status==1) {
                    //v.setEnabled(false);
                    v.setTag(0);
                    stopLeScan();
                    //connectionStatusText.setText("Connecting...");
                    Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
                    isBound = getApplicationContext().bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
                }
                //If we are disconnecting
                else
                {
                    connectButton.setText("CONNECT");
                    v.setTag(1);
                    if(isBound) getApplicationContext().unbindService(rfduinoServiceConnection);
                    downgradeState(STATE_DISCONNECTED);

                    //rfduinoService.close();
                    //rfduinoService.disconnect();

                    scanButton.setEnabled(true);
                }
            }
        });

        // Send
        /*valueEdit = (EditData) findViewById(R.id.value);
        valueEdit.setImeOptions(EditorInfo.IME_ACTION_SEND);
        valueEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendValueButton.callOnClick();
                    return true;
                }
                return false;
            }
        });*/


        resetButton = (Button) findViewById(R.id.reset);

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rfduinoService.send(new byte[]{8,0});
            }
        });

        sendZeroButton = (Button) findViewById(R.id.sendZero);
        sendZeroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rfduinoService.send(new byte[]{0,0});
            }
        });

        recordOnDeviceButton = (Button) findViewById(R.id.recordOnDeviceButtonID);
        recordOnDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toggle UI logging
                onDeviceLogging = !onDeviceLogging;

                if(onDeviceLogging)
                {
                    View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, dataLayout, false);
                    TextView text = (TextView) view.findViewById(android.R.id.text1);
                    text.setTextSize(12);

                    SimpleDateFormat s = new SimpleDateFormat("ddhhmmss");
                    String format = s.format(new Date());
                    uiLogFile = new File("sdcard/Logs/"+format+".log");
                    try
                    {
                        uiLogFile.createNewFile();
                        text.setText("Started logging to "+format+".log");
                        view.setLayoutParams(new LinearLayout.LayoutParams(-1, 60));
                        dataLayout.addView(view, 0);
                    }
                    catch (IOException e) {
                        // TODO Auto-generated catch block
                        text.setText("ERROR starting log file!");
                        e.printStackTrace();
                    }
                    recordOnDeviceButton.getBackground().setColorFilter(Color.DKGRAY, PorterDuff.Mode.MULTIPLY);
                }
                else
                {
                    recordOnDeviceButton.getBackground().setColorFilter(Color.LTGRAY, PorterDuff.Mode.MULTIPLY);
                }
            }
        });

        sendValueButton = (Button) findViewById(R.id.sendValue);
        sendValueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //rfduinoService.send(valueEdit.getData());
                rfduinoService.send(new byte[]{1,0});
            }
        });

        // Receive
        /*clearButton = (Button) findViewById(R.id.clearData);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataLayout.removeAllViews();
            }
        });*/

        deviceList = (Spinner) findViewById(R.id.deviceList);
        ArrayAdapter<CharSequence> deviceListAdapter = ArrayAdapter.createFromResource(this,
                R.array.device_list, android.R.layout.simple_spinner_item);
        deviceListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceList.setAdapter(deviceListAdapter);
        deviceList.setOnItemSelectedListener(this);

        chartSelect = (Spinner) findViewById(R.id.dataTypeDropdown);
        ArrayAdapter<CharSequence> dataTypeAdapter = ArrayAdapter.createFromResource(this,
                R.array.data_types, android.R.layout.simple_spinner_item);
        dataTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chartSelect.setAdapter(dataTypeAdapter);
        chartSelect.setOnItemSelectedListener(this);

        dataLayout = (LinearLayout) findViewById(R.id.dataLayout);

        //Open data file
        SimpleDateFormat s = new SimpleDateFormat("ddhhmmss");
        String format = s.format(new Date());
        logFile = new File("sdcard/Logs/"+format+".log");
        try
        {
            logFile.createNewFile();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
    {
        switch (adapterView.getId()){
            case R.id.deviceList:
                selectedDeviceIndex = i;
                break;
            case R.id.dataTypeDropdown:
                graphLastXValue = 0; //reset time
                ECGLastIndex = 0; //reset time

                //Log.d("WiSPR", "Selected item: "+adapterView.getItemAtPosition(i).toString());
                switch(adapterView.getItemAtPosition(i).toString())
                {
                    /*case "PPG":
                        //Remove previous data sets
                        while(lineData.getDataSetCount()>0) lineData.removeDataSet(0);

                        //Add PPG data sets
                        lineData.addDataSet(redDataSet);
                        lineData.addDataSet(irDataSet);
                        lineData.addDataSet(greenDataSet);

                        //Update chart formating
                        chart.getAxisLeft().setDrawLabels(false);
                        chart.getAxisLeft().setDrawAxisLine(false);
                        chart.getAxisLeft().setDrawGridLines(false);
                        chart.getAxisLeft().setStartAtZero(false);
                        chart.getAxisRight().setStartAtZero(false);
                        chart.setAutoScaleMinMaxEnabled(true);

                        //Update data and chart
                        lineData.notifyDataChanged();
                        chart.notifyDataSetChanged();
                        chart.invalidate();

                        if(rfduinoService!=null) rfduinoService.send(new byte[]{4,0}); //Tell WiSPR to stream PPG

                        break;*/
                    case "COMBINED":
                        ////////////////////////////////////
                        //Set up PPG Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData.getDataSetCount()>0) lineData.removeDataSet(0);

                        //Add PPG data sets
                        lineData.addDataSet(redDataSet);
                        lineData.addDataSet(irDataSet);

                        //Update chart formating
                        chart.getAxisLeft().setDrawLabels(false);
                        chart.getAxisLeft().setDrawAxisLine(false);
                        chart.getAxisLeft().setDrawGridLines(false);
                        chart.getAxisLeft().setStartAtZero(false);
                        chart.getAxisRight().setStartAtZero(false);
                        chart.setAutoScaleMinMaxEnabled(true);

                        //Update data and chart
                        lineData.notifyDataChanged();
                        chart.notifyDataSetChanged();
                        chart.invalidate();

                        ////////////////////////////////////
                        //Set up ECG Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData2.getDataSetCount()>0) lineData2.removeDataSet(0);

                        //Add HR data sets
                        lineData2.addDataSet(hrDataSet);

                        //Update chart formatting for HR
                        chart2.getAxisLeft().setDrawZeroLine(true);
                        //chart2.getAxisLeft().setAxisMinimum(-5000f); // start at zero
                        //chart2.getAxisLeft().setAxisMaximum(5000f); // the axis maximum is 100
                        chart2.getAxisLeft().setDrawLabels(false);
                        chart2.getAxisLeft().setDrawAxisLine(false);
                        chart2.getAxisLeft().setDrawGridLines(true);
                        //chart2.getAxisLeft().setStartAtZero(true);
                        //chart2.getAxisLeft().setAxisMaximum(120.0f);
                        chart2.setAutoScaleMinMaxEnabled(true);


                        //Update data and chart
                        lineData2.notifyDataChanged();
                        chart2.notifyDataSetChanged();
                        chart2.invalidate();

                        ////////////////////////////////////
                        //Set up Accel Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData3.getDataSetCount()>0) lineData3.removeDataSet(0);

                        //Add PPG data sets
                        lineData3.addDataSet(xDataSet);
                        lineData3.addDataSet(yDataSet);
                        lineData3.addDataSet(zDataSet);

                        //Update chart formating
                        chart3.getAxisLeft().setDrawLabels(true);
                        chart3.getAxisLeft().setDrawAxisLine(false);
                        chart3.getAxisLeft().setDrawGridLines(false);
                        chart3.getAxisLeft().setAxisMinimum(-2f);
                        chart3.getAxisLeft().setAxisMaximum(2f);
                        chart3.setAutoScaleMinMaxEnabled(false);

                        //Update data and chart
                        lineData3.notifyDataChanged();
                        chart3.notifyDataSetChanged();
                        chart3.invalidate();

                        if(rfduinoService!=null) rfduinoService.send(new byte[]{7,0}); //Tell WiSPR to stream COMBINED DATA

                        break;
                    /*case "ACCEL":
                        //Remove previous data sets
                        while(lineData.getDataSetCount()>0) lineData.removeDataSet(0);

                        //Remove points that are older than five seconds
                        xDataSet.clear();
                        xDataSet.addEntry(new Entry((float)graphLastXValue, 0));
                        yDataSet.clear();
                        yDataSet.addEntry(new Entry((float)graphLastXValue, 0));
                        zDataSet.clear();
                        zDataSet.addEntry(new Entry((float)graphLastXValue, 0));

                        //Add PPG data sets
                        lineData.addDataSet(xDataSet);
                        lineData.addDataSet(yDataSet);
                        lineData.addDataSet(zDataSet);

                        //Update chart formating
                        chart.getAxisLeft().setDrawLabels(false);
                        chart.getAxisLeft().setDrawAxisLine(false);
                        chart.getAxisLeft().setDrawGridLines(false);
                        chart.getAxisLeft().setStartAtZero(false);
                        chart.getAxisRight().setStartAtZero(false);
                        chart.setAutoScaleMinMaxEnabled(true);

                        //Update data and chart
                        lineData.notifyDataChanged();
                        chart.notifyDataSetChanged();
                        chart.invalidate();

                        if(rfduinoService!=null) rfduinoService.send(new byte[]{5,0}); //Tell WiSPR to stream PPG

                        break;
                    case "ECG":
                        ////////////////////////////////////
                        //Set up RAW ECG Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData.getDataSetCount()>0) lineData.removeDataSet(0);

                        //Add ECG data set
                        lineData.addDataSet(hrDataSet);

                        //Update chart formatting for HR
                        chart.getAxisLeft().setDrawZeroLine(true);
                        chart.getAxisLeft().setDrawLabels(false);
                        chart.getAxisLeft().setDrawAxisLine(false);
                        chart.getAxisLeft().setDrawGridLines(true);
                        //chart.getAxisLeft().setStartAtZero(true);
                        //chart.getAxisLeft().setAxisMaximum(120.0f);
                        chart.setAutoScaleMinMaxEnabled(true);


                        //Update data and chart
                        lineData.notifyDataChanged();
                        chart.notifyDataSetChanged();
                        chart.invalidate();

                        ////////////////////////////////////
                        //Set up PLI Filtered ECG Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData2.getDataSetCount()>0) lineData2.removeDataSet(0);

                        //Add HR data sets
                        lineData2.addDataSet(ecgFiltDataSet);

                        //Update chart formatting for HR
                        chart2.getAxisLeft().setDrawZeroLine(true);
                        //chart2.getAxisLeft().setAxisMinimum(-5000f); // start at zero
                        //chart2.getAxisLeft().setAxisMaximum(5000f); // the axis maximum is 100
                        chart2.getAxisLeft().setDrawLabels(false);
                        chart2.getAxisLeft().setDrawAxisLine(false);
                        chart2.getAxisLeft().setDrawGridLines(true);
                        //chart2.getAxisLeft().setStartAtZero(true);
                        //chart2.getAxisLeft().setAxisMaximum(120.0f);
                        chart2.setAutoScaleMinMaxEnabled(true);

                        //Update data and chart
                        lineData2.notifyDataChanged();
                        chart2.notifyDataSetChanged();
                        chart2.invalidate();

                        ////////////////////////////////////
                        //Set up BPM Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData3.getDataSetCount()>0) lineData3.removeDataSet(0);

                        //Add HR data sets
                        lineData3.addDataSet(bpmDataSet);

                        //Update chart formatting for HR
                        //Update chart formating
                        chart3.getAxisLeft().setDrawLabels(true);
                        chart3.getAxisLeft().setDrawAxisLine(false);
                        chart3.getAxisLeft().setDrawGridLines(false);
                        chart3.getAxisLeft().setStartAtZero(true);
                        chart3.getAxisLeft().setAxisMaximum(300.0f);
                        chart3.setAutoScaleMinMaxEnabled(false);

                        //Update data and chart
                        lineData3.notifyDataChanged();
                        chart3.notifyDataSetChanged();
                        chart3.invalidate();

                        if(rfduinoService!=null) rfduinoService.send(new byte[]{6,0}); //Tell WiSPR to stream hr

                        break;*/

                    case "GEN4":

                        Log.d("WiSPR", "Setting up Gen4 display");

                        ////////////////////////////////////
                        //Set up ECG Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData.getDataSetCount()>0) lineData.removeDataSet(0);

                        //Add ECG and HB data sets
                        lineData.addDataSet(ecgDataSet);
                        lineData.addDataSet(hbDataSet);

                        //Update chart formatting for HR
                        chart.getAxisLeft().setDrawZeroLine(true);
                        //chart.getAxisLeft().setAxisMinimum(-5000f); // start at zero
                        //chart.getAxisLeft().setAxisMaximum(5000f); // the axis maximum is 100
                        chart.getAxisLeft().setDrawLabels(false);
                        chart.getAxisLeft().setDrawAxisLine(false);
                        chart.getAxisLeft().setDrawGridLines(true);
                        chart.getXAxis().setTextColor(Color.WHITE);
                        //chart.getAxisLeft().setStartAtZero(true);
                        //chart.getAxisLeft().setAxisMaximum(120.0f);
                        chart.setAutoScaleMinMaxEnabled(true);

                        //Update data and chart
                        lineData.notifyDataChanged();
                        chart.notifyDataSetChanged();
                        chart.invalidate();


                        ////////////////////////////////////
                        //Set up Accel Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData2.getDataSetCount()>0) lineData2.removeDataSet(0);

                        //Add PPG data sets
                        lineData2.addDataSet(xDataSet);
                        lineData2.addDataSet(yDataSet);
                        lineData2.addDataSet(zDataSet);

                        //Update chart formating
                        chart2.getAxisLeft().setDrawLabels(true);
                        chart2.getAxisLeft().setDrawAxisLine(false);
                        chart2.getAxisLeft().setDrawGridLines(false);
                        //chart2.getAxisLeft().setAxisMinimum(-2f);
                        //chart2.getAxisLeft().setAxisMaximum(2f);
                        chart2.setAutoScaleMinMaxEnabled(true);
                        chart2.getAxisLeft().setTextColor(Color.WHITE);
                        chart2.getXAxis().setTextColor(Color.WHITE);

                        //Update data and chart
                        lineData2.notifyDataChanged();
                        chart2.notifyDataSetChanged();
                        chart2.invalidate();

                        ////////////////////////////////////
                        //Set up PPG Chart
                        ////////////////////////////////////
                        //Remove previous data sets
                        while(lineData3.getDataSetCount()>0) lineData3.removeDataSet(0);

                        //Add PPG data sets
                        lineData3.addDataSet(redDataSet);
                        lineData3.addDataSet(irDataSet);

                        //Update chart formating
                        chart3.getAxisLeft().setDrawLabels(false);
                        chart3.getAxisLeft().setDrawAxisLine(false);
                        chart3.getAxisLeft().setDrawGridLines(false);
                        chart3.getAxisLeft().setStartAtZero(false);
                        chart3.getAxisRight().setStartAtZero(false);
                        chart3.setAutoScaleMinMaxEnabled(true);

                        //Update data and chart
                        lineData3.notifyDataChanged();
                        chart3.notifyDataSetChanged();
                        chart3.invalidate();

                        if(rfduinoService!=null) rfduinoService.send(new byte[]{7,0}); //Tell WiSPR to stream COMBINED DATA

                        break;
                }

                break;
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    public void initChartData()
    {
        //Set up red data set
        List<Entry> redEntries = new ArrayList<Entry>();
        redDataSet = new LineDataSet(redEntries, "RED    "); // add entries to dataset
        redDataSet.setColor(Color.RED);
        redDataSet.setCircleColor(Color.RED);
        redDataSet.setCircleRadius(1.0f);
        redDataSet.setDrawValues(false);
        redDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++)
            redDataSet.addEntry(new Entry((i*20.0f)/1000.0f, 0));
        redDataSet.setDrawValues(false);

        //Set up IR data set
        List<Entry> irEntries = new ArrayList<Entry>();
        irDataSet = new LineDataSet(irEntries, "IR    "); // add entries to dataset
        irDataSet.setColor(Color.GRAY);
        irDataSet.setCircleColor(Color.GRAY);
        irDataSet.setCircleRadius(1.0f);
        irDataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        for(float i=0; i<150.0; i++)
            irDataSet.addEntry(new Entry((i*20.0f)/1000.0f, 0));
        irDataSet.setDrawValues(false);

        //Set up GREEN data set
        List<Entry> greenEntries = new ArrayList<Entry>();
        greenDataSet = new LineDataSet(greenEntries, "GREEN"); // add entries to dataset
        greenDataSet.setColor(Color.GREEN);
        greenDataSet.setCircleColor(Color.GREEN);
        greenDataSet.setCircleRadius(1.0f);
        greenDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++)
            greenDataSet.addEntry(new Entry((i*20.0f)/1000.0f, 0));
        greenDataSet.setDrawValues(false);

        //Set up x accel data set
        List<Entry> xEntries = new ArrayList<Entry>();
        xDataSet = new LineDataSet(xEntries, "X_g    "); // add entries to dataset
        xDataSet.setColor(Color.RED);
        xDataSet.setCircleColor(Color.RED);
        xDataSet.setDrawCircles(false);
        xDataSet.setDrawValues(false);
        xDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++) {
            xDataSet.addEntry(new Entry((i * 20.0f) / 1000.0f, 0));
            xDataSet.addColor(Color.RED);
        }
        xDataSet.setDrawValues(false);

        //Set up y accel data set
        List<Entry> yEntries = new ArrayList<Entry>();
        yDataSet = new LineDataSet(yEntries, "Y_g    "); // add entries to dataset
        yDataSet.setColor(Color.GREEN);
        yDataSet.setCircleColor(Color.GREEN);
        yDataSet.setDrawCircles(false);
        yDataSet.setDrawValues(false);
        yDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++) {
            yDataSet.addEntry(new Entry((i * 20.0f) / 1000.0f, 0));
            yDataSet.addColor(Color.GREEN);
        }
        yDataSet.setDrawValues(false);

        //Set up z accel data set
        List<Entry> zEntries = new ArrayList<Entry>();
        zDataSet = new LineDataSet(zEntries, "Z_g    "); // add entries to dataset
        zDataSet.setColor(Color.BLUE);
        zDataSet.setCircleColor(Color.BLUE);
        zDataSet.setDrawCircles(false);
        zDataSet.setDrawValues(false);
        zDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++) {
            zDataSet.addEntry(new Entry((i * 20.0f) / 1000.0f, 0));
            zDataSet.addColor(Color.BLUE);
        }
        zDataSet.setDrawValues(false);

        //Set up processor temp data set
        List<Entry> procEntries = new ArrayList<Entry>();
        processorTempDataSet = new LineDataSet(procEntries, "proc_degF    "); // add entries to dataset
        processorTempDataSet.setColor(Color.RED);
        processorTempDataSet.setCircleColor(Color.RED);
        processorTempDataSet.setCircleRadius(1.0f);
        processorTempDataSet.setDrawValues(false);
        processorTempDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        processorTempDataSet.addEntry(new Entry(0, 0));
        processorTempDataSet.setDrawValues(false);

        //Set up accel temp data set
        List<Entry> accelEntries = new ArrayList<Entry>();
        accelTempDataSet = new LineDataSet(accelEntries, "accel_degF    "); // add entries to dataset
        accelTempDataSet.setColor(Color.GREEN);
        accelTempDataSet.setCircleColor(Color.GREEN);
        accelTempDataSet.setCircleRadius(1.0f);
        accelTempDataSet.setDrawValues(false);
        accelTempDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        accelTempDataSet.addEntry(new Entry(0, 0));
        accelTempDataSet.setDrawValues(false);

        //Set up pulseox temp data set
        List<Entry> poEntries = new ArrayList<Entry>();
        pulseOxTempDataSet = new LineDataSet(poEntries, "accel_degF    "); // add entries to dataset
        pulseOxTempDataSet.setColor(Color.BLUE);
        pulseOxTempDataSet.setCircleColor(Color.BLUE);
        pulseOxTempDataSet.setCircleRadius(1.0f);
        pulseOxTempDataSet.setDrawValues(false);
        pulseOxTempDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        pulseOxTempDataSet.addEntry(new Entry(0, 0));
        pulseOxTempDataSet.setDrawValues(false);

        //Set up HR data set
        List<Entry> hrEntries = new ArrayList<Entry>();
        hrDataSet = new LineDataSet(hrEntries, "ECG    "); // add entries to dataset
        hrDataSet.setCircleRadius(1.0f);
        hrDataSet.setDrawValues(false);
        hrDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++) {
            hrDataSet.addEntry(new Entry((i * 20.0f) / 1000.0f, 0));
            hrDataSet.addColor(Color.GREEN);
        }
        hrDataSet.setDrawValues(false);

        //Set up ECG data set
        List<Entry> ecgEntries = new ArrayList<Entry>();
        ecgDataSet = new LineDataSet(ecgEntries, "ECG    "); // add entries to dataset
        //ecgDataSet.setCircleRadius(1.0f);
        ecgDataSet.setDrawCircles(false);
        ecgDataSet.setDrawValues(false);
        ecgDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++) {
            ecgDataSet.addEntry(new Entry((i * 20.0f) / 1000.0f, 0));
            ecgDataSet.addColor(Color.GREEN);
        }
        ecgDataSet.setDrawValues(false);

        //Set up heart beat data set
        List<Entry> hbEntries = new ArrayList<Entry>();
        hbDataSet = new LineDataSet(hbEntries, "HB    "); // add entries to dataset
        //hbDataSet.setCircleRadius(2.0f);
        hbDataSet.setDrawValues(false);
        hbDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        hbDataSet.addEntry(new Entry(0, 0));
        hbDataSet.setDrawValues(false);

        //Set up PLI ECG temp data set
        List<Entry> hrPLIEntries = new ArrayList<Entry>();
        ecgFiltDataSet = new LineDataSet(hrPLIEntries, "ECG PLI "); // add entries to dataset
        ecgFiltDataSet.setColor(Color.RED);
        ecgFiltDataSet.setCircleColor(Color.RED);
        ecgFiltDataSet.setCircleRadius(1.0f);
        ecgFiltDataSet.setDrawValues(false);
        ecgFiltDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        for(float i=0; i<150.0; i++)
            ecgFiltDataSet.addEntry(new Entry((i*20.0f)/1000.0f, 0));
        ecgFiltDataSet.setDrawValues(false);

        //Set up BPM data set
        List<Entry> bpmEntries = new ArrayList<Entry>();
        bpmDataSet = new LineDataSet(bpmEntries, "BPM "); // add entries to dataset
        bpmDataSet.setColor(Color.RED);
        bpmDataSet.setCircleColor(Color.RED);
        bpmDataSet.setCircleRadius(1.0f);
        bpmDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        bpmDataSet.addEntry(new Entry(0, 0));
        bpmDataSet.setDrawValues(false);
    }

    public void initChart(LineChart chart, LineData lData)
    {
        //Initially add PPG data sets
        //dataSets.add(redDataSet);
        //dataSets.add(irDataSet);
        //dataSets.add(greenDataSet);

        chart.setData(lData);

        //Chart Formatting
        chart.getAxisLeft().setDrawLabels(false);
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisRight().setDrawLabels(false);
        chart.getAxisRight().setDrawAxisLine(false);
        chart.getAxisRight().setDrawGridLines(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextSize(15f);
        chart.getXAxis().setDrawLabels(true);
        chart.getLegend().setPosition(Legend.LegendPosition.RIGHT_OF_CHART_INSIDE);
        chart.getLegend().setTextSize(15f);
        chart.getDescription().setEnabled(false);
        chart.setExtraBottomOffset(5);
        chart.setTouchEnabled(false);
    }

    /*public void initGraph(GraphView graph) {
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(5);

        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        graph.getGridLabelRenderer().setLabelVerticalWidth(100);

        // Initialize all series
        mSeriesA = new LineGraphSeries<>();
        //mSeriesA.setDrawDataPoints(true);
        //mSeriesA.setDrawBackground(true);
        mSeriesA.setColor(Color.RED);
        graph.addSeries(mSeriesA);

        // first mSeries is a line
        mSeriesB = new LineGraphSeries<>();
        //mSeriesB.setDrawDataPoints(true);
        //mSeriesB.setDrawBackground(true);
        mSeriesB.setColor(Color.GRAY);
        graph.addSeries(mSeriesB);

        // first mSeries is a line
        mSeriesC = new LineGraphSeries<>();
        //mSeriesC.setDrawDataPoints(true);
        //mSeriesC.setDrawBackground(true);
        mSeriesC.setColor(Color.GREEN);
        //graph.addSeries(mSeriesC);
        graph.getSecondScale().addSeries(mSeriesC);
        graph.getSecondScale().setMinY(0);
        graph.getSecondScale().setMaxY(100);
        graph.getSecondScale().calcCompleteRange();
        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
    }*/

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {
        // Enable Bluetooth
        boolean on = state > STATE_BLUETOOTH_OFF;
        //enableBluetoothButton.setEnabled(!on);
        //enableBluetoothButton.setText(on ? "Bluetooth enabled" : "Enable Bluetooth");
        scanButton.setEnabled(on);

        // Scan
        if (scanStarted && scanning) {
            //scanStatusText.setText("Scanning...");
            scanButton.setText("Stop Scan");
            scanButton.setEnabled(true);
        } else if (scanStarted) {
            //scanStatusText.setText("Scan started...");
            scanButton.setEnabled(false);
        } else {
            //scanStatusText.setText("");
            scanButton.setText("Scan");
            scanButton.setEnabled(true);
        }

        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
            ActionBar actionBar = this.getActionBar();
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#a3d4f7")));
            connectButton.setText("DISCONNECT");
            connectButton.setEnabled(true);
        }
        /*else if (state == STATE_DISCONNECTING) {
            connectionText = "Disconnecting...";
        }*/
        else
        {
            ActionBar actionBar = this.getActionBar();
            actionBar.setBackgroundDrawable(new ColorDrawable(Color.LTGRAY));
        }
        this.setTitle("WiSPR ("+connectionText+")");
        //connectionStatusText.setText(connectionText);

        //if(deviceList.getSelectedItem()==null) Log.d("WiSPR", "deviceList.getSelectedItem() returns null");
        //else Log.d("WiSPR", "deviceList.getSelectedItem() returns "+deviceList.getSelectedItem().toString());

        if(     deviceList.getSelectedItem()==null ||
                deviceList.getSelectedItem().toString().equals("SCAN FOR DEVICES") )
        {
            //Log.d("WiSPR", "setting enabled false");
            connectButton.setEnabled(false);
        }
        else
        {
            //Log.d("WiSPR", "setting enabled true");
            connectButton.setEnabled(true);
        }

        // Enable/disable certain controls
        chartSelect.setEnabled(connected);
        sendZeroButton.setEnabled(connected);
        recordOnDeviceButton.setEnabled(connected);
        sendValueButton.setEnabled(connected);
        resetButton.setEnabled(connected);
    }

    private void appendLog(String text)
    {
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));

            SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmssSSS");
            String format = s.format(new Date());
            buf.append(format);

            buf.append(","+text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void appendLog(byte data[])
    {
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(logFile, true));
            bos.write(data,2,data.length - 2); //dont send header and packet counter
            bos.flush();
            bos.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void logDataToDevice(byte type, double time, double value)
    {
        if (!uiLogFile.exists())
        {
            try
            {
                uiLogFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(uiLogFile, true));

            buf.append(type+","+time+","+value);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void logDataToDevice(byte type, double time, ArrayList<Float> values)
    {
        if (!uiLogFile.exists())
        {
            try
            {
                uiLogFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(uiLogFile, true));

            buf.append(type+","+time+",");

            for (int valueIndex=0; valueIndex<values.size(); valueIndex++) {
                buf.append(values.get(valueIndex)+",");
            }

            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private Pair<Integer, Integer> readIntegerValue(String ascii)
    {
        Integer counter  = new Integer(-1);
        Integer value  = new Integer(-1);
        int index = 0;
        Scanner scanner = new Scanner(ascii);
        while (scanner.hasNextInt()) {

            if(index==0)
            {
                counter = scanner.nextInt();
            }
            else
            {
                value = scanner.nextInt();
            }
            index++;
        }

        return new Pair<Integer, Integer>(counter, value);
    }

    private static int fromTwoComplement(int value, int bitSize) {
        int shift = Integer.SIZE - bitSize;
        // shift sign into position
        int result  = value << shift;
        // Java right shift uses sign extension, but only works on integers or longs
        result = result >> shift;
        return result;
    }

    private ArrayList<Float> parseCombinedMixed(byte bytes[])
    {
        ArrayList<Float> retVal = new ArrayList<Float>();

        Integer redData = ((bytes[4] & 0xFF) << 24) | ((bytes[3] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[1] & 0xFF);

        Integer irData = ((bytes[8] & 0xFF) << 24) | ((bytes[7] & 0xFF) << 16)
                | ((bytes[6] & 0xFF) << 8) | (bytes[5] & 0xFF);

        //int redValue = Integer.valueOf(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt());
        retVal.add(redData.floatValue());

        //int irValue = Integer.valueOf(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt());
        retVal.add(irData.floatValue());

        Short nextValue = new Short(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 9, 11)).order(ByteOrder.LITTLE_ENDIAN).getShort());
        //short ECGValue = Short.valueOf(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 8, 10)).order(ByteOrder.LITTLE_ENDIAN).getShort());
        retVal.add(nextValue.floatValue());

        short XValue = Short.valueOf(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 11, 13)).order(ByteOrder.LITTLE_ENDIAN).getShort());
        float XValueF = (float)((float)fromTwoComplement((int)XValue, 16) * 0.061 / 1000.0);
        //Log.d("WiSPR", "Received X data: "+Float.valueOf(XValueF).toString());
        retVal.add(XValueF);

        short YValue = Short.valueOf(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 13, 15)).order(ByteOrder.LITTLE_ENDIAN).getShort());
        float YValueF = (float)((float)fromTwoComplement((int)YValue, 16) * 0.061 / 1000.0);
        retVal.add((float)YValueF);

        short ZValue = Short.valueOf(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 15, 17)).order(ByteOrder.LITTLE_ENDIAN).getShort());
        float ZValueF = (float)((float)fromTwoComplement((int)ZValue, 16) * 0.061 / 1000.0);
        retVal.add((float)ZValueF);


        /*Integer redData = ((bytes[4] & 0xFF) << 24) | ((bytes[3] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[1] & 0xFF);

        Integer irData = ((bytes[8] & 0xFF) << 24) | ((bytes[7] & 0xFF) << 16)
                | ((bytes[6] & 0xFF) << 8) | (bytes[5] & 0xFF);

        Integer greenData = ((bytes[12] & 0xFF) << 24) | ((bytes[11] & 0xFF) << 16)
                | ((bytes[10] & 0xFF) << 8) | (bytes[9] & 0xFF);

        ArrayList<Integer> retVal = new ArrayList<Integer>();
        retVal.add(redData);
        retVal.add(irData);
        retVal.add(greenData);

        //Log.d("WiSPR", "Received green data: "+greenData.toString());

        int totalValuesInPacket = ((bytes.length-1)/2);

        ArrayList<Short> retVal = new ArrayList<Short>();
        Short nextValue;
        for(int i=0; i<totalValuesInPacket; i++)
        {
            nextValue = new Short(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1+(i*2), 1+(i*2)+2)).order(ByteOrder.LITTLE_ENDIAN).getShort());
            retVal.add(nextValue);
        }*/

        return retVal;
    }

    private ArrayList<Integer> parseCombinedPPG(byte bytes[])
    {
        Integer redData = ((bytes[4] & 0xFF) << 24) | ((bytes[3] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[1] & 0xFF);

        Integer irData = ((bytes[8] & 0xFF) << 24) | ((bytes[7] & 0xFF) << 16)
                | ((bytes[6] & 0xFF) << 8) | (bytes[5] & 0xFF);

        Integer greenData = ((bytes[12] & 0xFF) << 24) | ((bytes[11] & 0xFF) << 16)
                | ((bytes[10] & 0xFF) << 8) | (bytes[9] & 0xFF);

        ArrayList<Integer> retVal = new ArrayList<Integer>();
        retVal.add(redData);
        retVal.add(irData);
        retVal.add(greenData);

        //Log.d("WiSPR", "Received green data: "+greenData.toString());

        return retVal;
    }

    private ArrayList<Float> parseCombinedAccel(byte bytes[])
    {
        //Parse accelerometer data
        Float x = new Float(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1, 5)).order(ByteOrder.LITTLE_ENDIAN).getFloat());
        Float y = new Float(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 5, 9)).order(ByteOrder.LITTLE_ENDIAN).getFloat());
        Float z = new Float(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 9, 13)).order(ByteOrder.LITTLE_ENDIAN).getFloat());

        ArrayList<Float> retVal = new ArrayList<Float>();
        retVal.add(x);
        retVal.add(y);
        retVal.add(z);

        //Log.d("WiSPR", "Received x data: "+x.toString());

        return retVal;
    }

    private ArrayList<Short> parseCombinedECG(byte bytes[])
    {
        int totalValuesInPacket = ((bytes.length-1)/2);

        ArrayList<Short> retVal = new ArrayList<Short>();
        Short nextValue;
        for(int i=0; i<totalValuesInPacket; i++)
        {
            nextValue = new Short(ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1+(i*2), 1+(i*2)+2)).order(ByteOrder.LITTLE_ENDIAN).getShort());
            retVal.add(nextValue);
        }

        Log.d("WiSPR", "Received ecg data: "+new Integer(totalValuesInPacket).toString());

        return retVal;
    }

    private void addData(byte[] data) {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, dataLayout, false);

        TextView text1 = (TextView) view.findViewById(android.R.id.text1);
        text1.setText(HexAsciiHelper.bytesToHex(data));

        String ascii = HexAsciiHelper.bytesToAsciiMaybe(data);

        if (ascii != null) {
            TextView text2 = (TextView) view.findViewById(android.R.id.text1);
            text2.setTextSize(12);
            text2.setText(ascii);
        }
        //Log.d("WiSPR", "Data: "+ascii);

        if(data[0]=='H')
        {
            Log.d("WiSPR", "Heartrate: "+ascii);

            double expectedUpdateRate_hz = 50;
            double visibleWindow_s = 3.0;
            graphLastXValue += (1.0/expectedUpdateRate_hz);

            //Log.d("WiSPR", "Received combined ACCEL message: "+ascii);
            ArrayList<Short> parsedData = parseCombinedECG(data);

            if(parsedData.size()==1)
            {
                List<Entry> values = hrDataSet.getValues();
                float xValue = hrDataSet.getEntryForIndex(ECGLastIndex).getX();
                values.set(ECGLastIndex, new Entry(xValue, (float) parsedData.get(0) * 12.247f / 8.5f / 9.0f)); //V_INPUT = ADC_CODE x 12.247μV/IA_GAIN/PGA_GAIN
                hrDataSet.setValues(values);
                lineData.notifyDataChanged(); // let the data know a dataSet changed
                chart.notifyDataSetChanged(); // let the chart know it's data changed
                chart.invalidate(); // refresh
            }
            else if(parsedData.size()==4) {
                //Update chart 1
                List<Entry> values = hrDataSet.getValues();
                float xValue = hrDataSet.getEntryForIndex(ECGLastIndex).getX();
                values.set(ECGLastIndex, new Entry(xValue, (float) parsedData.get(1) * 12.247f / 8.5f / 9.0f)); //V_INPUT = ADC_CODE x 12.247μV/IA_GAIN/PGA_GAIN
                hrDataSet.setValues(values);

                List<Integer> colors = hrDataSet.getColors();
                if(parsedData.get(3)==1) colors.set(ECGLastIndex, Color.RED);
                else colors.set(ECGLastIndex, Color.GREEN);
                hrDataSet.setCircleColors(colors);
                hrDataSet.setColors(colors);

                lineData.notifyDataChanged(); // let the data know a dataSet changed
                chart.notifyDataSetChanged(); // let the chart know it's data changed
                chart.invalidate(); // refresh

                //Update chart 2
                List<Entry> valuesPLI = ecgFiltDataSet.getValues();
                float xFiltValue = ecgFiltDataSet.getEntryForIndex(ECGLastIndex).getX();
                valuesPLI.set(ECGLastIndex, new Entry(xFiltValue, (float) parsedData.get(2) * 12.247f / 8.5f / 9.0f)); //V_INPUT = ADC_CODE x 12.247μV/IA_GAIN/PGA_GAIN
                ecgFiltDataSet.setValues(valuesPLI);
                lineData2.notifyDataChanged(); // let the data know a dataSet changed
                chart2.notifyDataSetChanged(); // let the chart know it's data changed
                chart2.invalidate(); // refresh

                //Update chart 3
                if(bpmDataSet.getValues().size()>(expectedUpdateRate_hz*visibleWindow_s)) bpmDataSet.removeFirst();
                bpmDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(0).shortValue()));
                lineData3.notifyDataChanged(); // let the data know a dataSet changed
                chart3.notifyDataSetChanged(); // let the chart know it's data changed
                chart3.invalidate(); // refresh

                heartRateText.setText(parsedData.get(3).toString());
            }

            ECGLastIndex ++;
            if(ECGLastIndex>=150) ECGLastIndex = 0; //wrap around


            if(onDeviceLogging) logDataToDevice((byte)'H', graphLastXValue, (double)parsedData.get(0));


        }
        else if(data[0]=='E' && data[1]==':')
        {
            String eventText = HexAsciiHelper.bytesToAsciiMaybe(Arrays.copyOfRange(data, 1, data.length));
            if (eventText != null) {
                String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());

                TextView text2 = (TextView) view.findViewById(android.R.id.text1);
                text2.setTextSize(12);
                text2.setText(currentDateTimeString+" (EVENT): "+eventText);
            }

            view.setLayoutParams(new LinearLayout.LayoutParams(-1, 60));
            dataLayout.addView(view, 0);
        }
        else if(data[0]=='T')
        {
            Log.d("WiSPR", "Temperature: "+ascii);

            Pair<Integer, Integer> retVals = readIntegerValue(ascii);

            //temperatureText.setTextSize(60);
            //temperatureText.setText(retVals.second.toString());

            //appendLog("3,"+retVals.first.toString()+","+retVals.second.toString());
        }
        else if(data[0]=='R')
        {
            //Log.d("WiSPR", "red: "+ascii);

            Pair<Integer, Integer> retVals = readIntegerValue(ascii);

            graphLastXValue += 0.04d; // assuming 25Hz
            //mSeriesA.appendData(new DataPoint(graphLastXValue, retVals.second/100.0), true, 250);
            //appendLog("0,"+retVals.first.toString()+","+retVals.second.toString());
        }
        else if(data[0]=='P')
        {
            //Log.d("WiSPR", "Received combined PPG message: "+ascii);

            ArrayList<Integer> parsedData = parseCombinedPPG(data);

            graphLastXValue += 0.04d;

            if(redDataSet.getValues().size()>75) redDataSet.removeFirst();
            redDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(0).floatValue()/100.0f));
            lineData.notifyDataChanged(); // let the data know a dataSet changed
            if(irDataSet.getValues().size()>75) irDataSet.removeFirst();
            irDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(1).floatValue()/100.0f));
            lineData.notifyDataChanged(); // let the data know a dataSet changed
            if(greenDataSet.getValues().size()>75) greenDataSet.removeFirst();
            greenDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(2).floatValue()/100.0f));
            lineData.notifyDataChanged(); // let the data know a dataSet changed

            chart.notifyDataSetChanged(); // let the chart know it's data changed
            chart.invalidate(); // refresh

        }
        else if(data[0]=='A')
        {
            //Log.d("WiSPR", "Received combined ACCEL message: "+ascii);

            ArrayList<Float> parsedData = parseCombinedAccel(data);

            graphLastXValue += 0.04d; // assuming 10Hz

            if(xDataSet.getValues().size()>75) xDataSet.removeFirst();
            xDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(0)));
            lineData.notifyDataChanged(); // let the data know a dataSet changed
            if(yDataSet.getValues().size()>75) yDataSet.removeFirst();
            yDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(1)));
            lineData.notifyDataChanged(); // let the data know a dataSet changed
            if(zDataSet.getValues().size()>75) zDataSet.removeFirst();
            zDataSet.addEntry(new Entry((float)graphLastXValue, parsedData.get(2)));
            lineData.notifyDataChanged(); // let the data know a dataSet changed

            chart.notifyDataSetChanged(); // let the chart know it's data changed
            chart.invalidate(); // refresh

        }
        else if(data[0]=='I')
        {
            //Log.d("WiSPR", "ir: "+ascii);

            Pair<Integer, Integer> retVals = readIntegerValue(ascii);

            //appendLog("1,"+retVals.first.toString()+","+retVals.second.toString());
        }
        else if(data[0]=='F') //header for file transfer
        {
            Log.e("WiSPR", "Received file header");
            view.setLayoutParams(new LinearLayout.LayoutParams(-1, 60));
            dataLayout.addView(view, 0);

            appendLog(data);
        }
        else if(data[0]=='C')
        {
            ArrayList<Float> parsedData = parseCombinedMixed(data);
            double expectedUpdateRate_hz = 50;
            double visibleWindow_s = 3.0;
            graphLastXValue += (1.0/expectedUpdateRate_hz);

            float lastECGValue = ecgDataSet.getEntryForIndex(ECGLastIndex).getY();

            ECGLastIndex ++;
            if(ECGLastIndex>=150) ECGLastIndex = 0; //wrap around

            //Update chart 1
            List<Entry> values = ecgDataSet.getValues();
            float timeValue = ecgDataSet.getEntryForIndex(ECGLastIndex).getX();
            float ecgValue = (float)parsedData.get(2)*12.247f/8.5f/9.0f;
            values.set(ECGLastIndex, new Entry(timeValue,ecgValue)); //V_INPUT = ADC_CODE x 12.247μV/IA_GAIN/PGA_GAIN
            ecgDataSet.setValues(values);
            List<Integer> colors = ecgDataSet.getColors();
            for(int i=0; i<150; i++)
            {
                if(colors.get(i) == Color.DKGRAY) colors.set(i, Color.GREEN);
                if(i>ECGLastIndex && i<ECGLastIndex+10) {
                    colors.set(i, Color.DKGRAY);
                }
            }
            float absDiffECG = ecgValue-lastECGValue;
            //Log.d("WiSPR", "absDiffECG: "+Float.valueOf(absDiffECG).toString());
            if((parsedData.get(0)>100 && absDiffECG<-300) || (parsedData.get(0)<100 && absDiffECG<-25))
            {
                //colors.set(ECGLastIndex, Color.RED);

                //Track rough HR
                long time = System.currentTimeMillis();
                if(heartBeatBuffer.size()==0 || time-heartBeatBuffer.getFirst()>600) heartBeatBuffer.addFirst(new Long(time));
                if(heartBeatBuffer.size()>10) heartBeatBuffer.removeLast();

                //Calculate diff array and median sort
                if(heartBeatBuffer.size()>2) {
                    long[] diffArray = new long[10];
                    for (int i = 0; i < heartBeatBuffer.size() - 1; i++) {
                        diffArray[i] = heartBeatBuffer.get(i)-heartBeatBuffer.get(i+1);
                        //Log.d("WiSPR", "diffArray ["+Integer.valueOf(i).toString()+"]: "+Float.valueOf(absDiffECG).toString());
                    }

                    double heartRate = 0;
                    long medValue = median(diffArray);
                    if(medValue!=0) heartRate = 1.0/(((double)medValue/1000.0)/60.0);

                    heartRate = 0.05*heartRate + 0.95*lastHR_bpm;
                    heartRateText.setText(new Integer((int)heartRate).toString());
                    lastHR_bpm = heartRate;
                }

            }
            ecgDataSet.setColors(colors);

            lineData.notifyDataChanged(); // let the data know a dataSet changed
            chart.notifyDataSetChanged(); // let the chart know it's data changed
            chart.invalidate(); // refresh

            //Update chart 2
            //smoothing function
            float smoothValue = smoothCalculator.CalculateSmooth(parsedData.get(3),parsedData.get(4), parsedData.get(5));
            //Log.d("WiSPR", "smoothValue"+Float.valueOf(smoothValue).toString());

            //Log.d("WiSPR", "smoothValue"+Float.valueOf(smooth.size()).toString());

            //peaks = new ArrayList<Integer>();
            double rr = smoothCalculator.calculateRR(smoothValue);
            Log.d("WiSPR", "rr: "+Double.valueOf(rr).toString());
            //peak_size = peaks.size();
            //if (peak_size > 5) {
            //    peaks.remove(0);
            //}


            List<Entry> xAccXvalues = xDataSet.getValues();
            float xAccXValue = xDataSet.getEntryForIndex(ECGLastIndex).getX();
            xAccXvalues.set(ECGLastIndex, new Entry(xAccXValue, smoothValue));
            List<Entry> xAccYvalues = yDataSet.getValues();
            float xAccYValue = yDataSet.getEntryForIndex(ECGLastIndex).getX();
            xAccYvalues.set(ECGLastIndex, new Entry(xAccYValue,parsedData.get(4)));
            List<Entry> xAccZvalues = zDataSet.getValues();
            float xAccZValue = zDataSet.getEntryForIndex(ECGLastIndex).getX();
            xAccZvalues.set(ECGLastIndex, new Entry(xAccZValue,parsedData.get(5)));

            List<Integer> colorsX = xDataSet.getColors();
            List<Integer> colorsY = yDataSet.getColors();
            List<Integer> colorsZ = zDataSet.getColors();
            for(int i=0; i<150; i++)
            {
                if(colorsX.get(i) == Color.DKGRAY) colorsX.set(i, Color.RED);
                if(colorsY.get(i) == Color.DKGRAY) colorsY.set(i, Color.GREEN);
                if(colorsZ.get(i) == Color.DKGRAY) colorsZ.set(i, Color.BLUE);
                if(i>ECGLastIndex && i<ECGLastIndex+10) {
                    colorsX.set(i, Color.DKGRAY);
                    colorsY.set(i, Color.DKGRAY);
                    colorsZ.set(i, Color.DKGRAY);
                }
            }
            xDataSet.setColors(colorsX);
            yDataSet.setColors(colorsY);
            zDataSet.setColors(colorsZ);

            lineData2.notifyDataChanged(); // let the data know a dataSet changed
            chart2.notifyDataSetChanged(); // let the chart know it's data changed
            chart2.invalidate(); // refresh

            double pitch_deg = (parsedData.get(5)+0.3)*90.0;
            //rr = smoothCalculator.calculateRR(peaks);
            double roll_deg = (parsedData.get(4)+0.3)*90.0;
            double accelMag = Math.sqrt( parsedData.get(3)*parsedData.get(3) + parsedData.get(4)*parsedData.get(4) + parsedData.get(5)*parsedData.get(5));
            DecimalFormat formatter = new DecimalFormat("00");
            //formatter.setRoundingMode( RoundingMode.DOWN );
            String accelString = formatter.format(rr);
            accelText.setText(accelString);
                    //+(char) 0x00B0);

            //Update chart 3 (ONLY IF WE HAVE VALID DATA)
            if(parsedData.get(0)>100) {
                List<Entry> redvalues = redDataSet.getValues();
                float redXValue = redDataSet.getEntryForIndex(ECGLastIndex).getX();
                redvalues.set(ECGLastIndex, new Entry(redXValue, parsedData.get(0).floatValue() / 100.0f));
                List<Entry> irvalues = irDataSet.getValues();
                float irXValue = irDataSet.getEntryForIndex(ECGLastIndex).getX();
                irvalues.set(ECGLastIndex, new Entry(irXValue, parsedData.get(1).floatValue() / 100.0f));
                lineData3.notifyDataChanged(); // let the data know a dataSet changed
                chart3.notifyDataSetChanged(); // let the chart know it's data changed
                chart3.invalidate(); // refresh
            }

            if(onDeviceLogging) logDataToDevice((byte)'C', graphLastXValue, parsedData);

        }
        else if(data[0]=='W')
        {
            int counter = data[1];
            Byte b = data[1];

            //Log.e("WiSPR", "Received packet " + b.toString());

            if(abs(abs(counter)-abs(lastCounter))>1) {
                missedPacketCounter++;
                if(missedPacketCounter>5)
                {
                    lastCounter = counter; //reset
                    missedPacketCounter = 0;
                }
                Log.e("WiSPR", "MISSED A PACKET!!!");
                rfduinoService.send(new byte[]{2,0});
            }
            else if(abs(abs(counter)-abs(lastCounter))==0) {
                Log.e("WiSPR", "RECEIVED DUPLICATE PACKET!!!");
                rfduinoService.send(new byte[]{3,0});
            }
            else {
                missedPacketCounter = 0;
                rfduinoService.send(new byte[]{3,b});
                lastCounter = counter;
                view.setLayoutParams(new LinearLayout.LayoutParams(-1, 60));
                dataLayout.addView(view, 0);
                appendLog(data); //write to disk
            }
        }
        else if(data[0]=='L')
        {
            int counter = data[1];
            Byte b = data[1];

            Log.e("WiSPR", "Received packet " + b.toString());
            view.setLayoutParams(new LinearLayout.LayoutParams(-1, 60));
            dataLayout.addView(view, 0);
        }
        else
        {
            view.setLayoutParams(new LinearLayout.LayoutParams(-1, 60));
            dataLayout.addView(view, 0);
        }

    }


    public void stopLeScan()
    {
        bluetoothAdapter.stopLeScan(this);
    }

    //sort the array, and return the median
    public long median(long[] a) {
        long temp;
        int asize = a.length;
        //sort the array in increasing order
        for (int i = 0; i < asize ; i++)
            for (int j = i+1; j < asize; j++)
                if (a[i] > a[j]) {
                    temp = a[i];
                    a[i] = a[j];
                    a[j] = temp;
                }
        //if it's odd
        if (asize%2 == 1)
            return a[asize/2];
        else
            return ((a[asize/2]+a[asize/2 - 1])/2);
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {

        if(device.getName() != null && device.getName().length()>0)
        {
            Log.d("WiSPR", device.getName());

            if(device.getName().equals("WiSPR"))
            {
                //Determine device
                String deviceName = "";
                if(device.getAddress().equals("D0:D7:E3:5B:21:48")) deviceName = "DEV_BOARD_1";
                if(device.getAddress().equals("D9:76:7F:D4:01:51")) deviceName = "DEV_BOARD_2";
                else if(device.getAddress().equals("D2:2A:1F:47:6E:65")) deviceName = "WiSPR v1";
                else if(device.getAddress().equals("FA:6F:9F:EC:73:5C")) deviceName = "WiSPR v2";
                else if(device.getAddress().equals("D8:2A:92:90:F3:02")) deviceName = "WiSPR v3";
                else if(device.getAddress().equals("CD:8B:D7:C4:38:DF")) deviceName = "WiSPR v4";
                else if(device.getAddress().equals("F3:90:63:CD:91:61")) deviceName = "Grey";
                else if(device.getAddress().equals("C1:8D:65:28:F6:5A")) deviceName = "Pink";
                else if(device.getAddress().equals("D9:69:82:C4:B6:D4")) deviceName = "Black";
                else if(device.getAddress().equals("C1:04:A2:B0:42:38")) deviceName = "Blue";
                else if(device.getAddress().equals("F5:2A:D7:D7:7B:E8")) deviceName = "Green Dragon";
                else deviceName = device.getAddress();

                //Add this to the connectable device list
                Spinner spinner = (Spinner)findViewById(R.id.deviceList);
                if(spinner.getAdapter().getCount()==1 && (spinner.getSelectedItem()==null || spinner.getSelectedItem().toString().equals("SCAN FOR DEVICES"))) {
                    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, android.R.id.text1);
                    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(spinnerAdapter);
                    bluetoothDeviceList.add(device);
                    spinnerAdapter.add(deviceName);
                    spinnerAdapter.notifyDataSetChanged();
                    spinner.setSelection(0);
                }
                else
                {
                    ArrayAdapter<String> spinnerAdapter = (ArrayAdapter<String>) spinner.getAdapter();

                    boolean isInList =false;
                    for (int index=0; index<spinnerAdapter.getCount(); index++) {
                        if(spinnerAdapter.getItem(index).equals(deviceName)){
                            isInList = true;
                        }
                    }

                    if(!isInList) {
                        bluetoothDeviceList.add(device);
                        spinnerAdapter.add(deviceName);
                        spinnerAdapter.notifyDataSetChanged();
                    }
                }

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //deviceInfoText.setText(
                        //       BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
                        updateUi();
                    }
                });
            }
        }
    }

}

