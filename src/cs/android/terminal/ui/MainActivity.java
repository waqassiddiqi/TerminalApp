package cs.android.terminal.ui;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import cs.android.terminal.R;
import cs.android.terminal.util.MiscUtil;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final String TAG = MainActivity.class.getSimpleName();
    private UsbSerialDriver mSerialDevice;
    private UsbManager mUsbManager;

    private EditText txtInput = null;
	private Button btnExecute = null;
	private ScrollView layoutOutputScroll = null;
	private TextView txtOutput = null;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
        	MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                	MainActivity.this.updateReceivedData(data);
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
                
        txtInput = (EditText) findViewById(R.id.txtInput);
        btnExecute = (Button) findViewById(R.id.btnExecute);
        layoutOutputScroll = (ScrollView) findViewById(R.id.layoutOutputScroll);
        txtOutput = (TextView) findViewById(R.id.txtOutput);
        
        btnExecute.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {
				
				StringBuilder sbOutput = new StringBuilder();
				
				if(mSerialDevice == null && mSerialIoManager == null) {
					sbOutput.append("No device or accessory can be opened");
					displayOutput(sbOutput);
					return;
				}
				
				sbOutput.append("No device or accessory can be opened");
				displayOutput(sbOutput);
				
				mSerialIoManager.writeAsync(txtInput.getText().toString().getBytes());
				
			}
		});
        
        initializeTerminal();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) { }
            
            mSerialDevice = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        enumerateDevices();
    }
    
    private void initializeTerminal() {
    	
    	StringBuilder sbOutput = new StringBuilder("Terminal loading"); 
    	
    	displayOutput(sbOutput);
    	
    	sbOutput.append("Checking USB status...");
		
		sbOutput.append("\n\t\t");
		sbOutput.append("Loading USB manager...");
		sbOutput.append("\n\t\t");
		
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		
		if(mUsbManager != null) {
			sbOutput.append("\t");
			sbOutput.append("USB manager loaded");
			
			displayOutput(sbOutput);
			
		} else {
			sbOutput.append("\t");
			sbOutput.append("Failed to load USB manager, exiting");
			return;
		}
    }
    
    private void enumerateDevices() {
    	if(mUsbManager.getDeviceList().size() > 0) {
    		setDevice();
    	} else {
    		displayOutput(new StringBuilder("No USB devices found"));
    	}
    }
    
    private void setDevice() {
    	
    	StringBuilder sbOutput = new StringBuilder("USB Devices: "
				+ mUsbManager.getDeviceList().size() + "\n");
    	
    	
    	UsbDevice device = mUsbManager.getDeviceList().entrySet().iterator().next().getValue();
    	
    	sbOutput.append("Device Name: " + device.getDeviceName());
    	
    	if (device.getInterfaceCount() <=0 ) {
			sbOutput.append("\t could not find interface \n\n");
			
			displayOutput(sbOutput);
			
			return;
        }
		
		UsbInterface intf = device.getInterface(0);
		
		if (intf.getEndpointCount() <= 0) {
			sbOutput.append("\t could not find endpoint \n\n");
			
			displayOutput(sbOutput);
			
			return;
		}		
		
		getDeviceInterfaces(device, sbOutput);
		displayOutput(sbOutput);
		
    	mSerialDevice = UsbSerialProber.acquire(mUsbManager);
    	
        Log.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
        
        if (mSerialDevice == null) {
            displayOutput(new StringBuilder("Error opening device"));
        } else {
            try {
                mSerialDevice.open();
            } catch (IOException e) {
            	displayOutput(new StringBuilder("Error opening device"));
                try {
                    mSerialDevice.close();
                } catch (IOException e2) { }
                
                mSerialDevice = null;
                return;
            }
            
            displayOutput(new StringBuilder("Connected to device: " + mSerialDevice));
        }
        
        onDeviceStateChange();
    }
    
    private void getDeviceInterfaces(UsbDevice device, StringBuilder sbOutput) {
		
		for(int idx = 0 ; idx < device.getInterfaceCount(); idx++) {
			UsbInterface usbInterface = device.getInterface(idx);
			sbOutput.append("\n\t\tUSB Interface: "+usbInterface.getId());
			
			for(int ndx = 0 ; ndx < usbInterface.getEndpointCount(); ndx++){
				UsbEndpoint usbEndpoint = usbInterface.getEndpoint(ndx);
				
				
				sbOutput.append("\n\t\t\t\t\tEndpoint: "
						+ usbEndpoint.getEndpointNumber() + " [" + (usbEndpoint.getDirection() == 0 ? "out" : "in")
						+ "] " + " " + MiscUtil.getEndPointTypeName(usbEndpoint.getType()) 
						+ " at " + usbEndpoint.getAddress());				
			}
		}
		
		displayOutput(sbOutput);
	}

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n\n";
        displayOutput(new StringBuilder(message));
    }
    
    private void displayOutput(StringBuilder sbOutput) {

		Log.d("TerminalApp", (sbOutput != null) ? sbOutput.toString() : "null");
		
		try {
			
			if(sbOutput != null && sbOutput.length() > 0) {
				sbOutput.insert(0, "> ");
				sbOutput.append("\n");
				
				txtOutput.append(sbOutput.toString());
				
				layoutOutputScroll.smoothScrollTo(0, txtOutput.getBottom());
				
				sbOutput.setLength(0);
			}
			
		} catch(Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

}
