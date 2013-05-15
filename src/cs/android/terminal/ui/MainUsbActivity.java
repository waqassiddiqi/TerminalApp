package cs.android.terminal.ui;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import cs.android.terminal.Constants;

import cs.android.terminal.R;
import cs.android.terminal.util.MiscUtil;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainUsbActivity extends Activity implements Runnable {
	
	private Queue<String> mQueueCommand = new ConcurrentLinkedQueue<String>();
	private HashMap<String, UsbDevice> mUsbDevices = new HashMap<String, UsbDevice>();
	private UsbEndpoint mUsbEndpoint = null;
	private UsbDeviceConnection mConnection = null;
	private UsbManager mUsbManager = null;  
	private UsbDevice mDevice = null;
	private int mEndPointType = -1;
	private UsbAccessory[] mUsbAccessories;
	private ParcelFileDescriptor mFileDescriptor;
	private FileInputStream mInputStream;
	private FileOutputStream mOutputStream;
	private UsbAccessory mUsbAccessory = null;
	
	private TextView textOutput = null;
	private Button btnExecute = null;
	private ScrollView scrollOutput = null;
	private EditText editInput = null;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		textOutput = (TextView) findViewById(R.id.txtOutput);
		btnExecute = (Button) findViewById(R.id.btnExecute);
		scrollOutput = (ScrollView) findViewById(R.id.layoutOutputScroll);
		editInput = (EditText) findViewById(R.id.txtInput);
		
		displayOutput("Terminal loading");
		
		initScan();
		
		btnExecute.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				if(mUsbAccessory == null && mDevice == null) {
					displayOutput("No device or accessory can be opened");
					return;
				}
				
				if(mUsbAccessory != null)
					sendCommandToAccessory(editInput.getText().toString());
				
				if(mDevice != null)
					sendCommand(editInput.getText().toString());
				
				editInput.setText("");
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		handleIntent(getIntent());
		
		IntentFilter usbDeviceFilter = new IntentFilter();
		usbDeviceFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		usbDeviceFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbDeviceReceiver, usbDeviceFilter);
		
		IntentFilter usbAccessoryFilter = new IntentFilter();
		usbAccessoryFilter.addAction("android.hardware.usb.action.USB_ACCESSORY_ATTACHED");
		registerReceiver(mUsbAccessoryReceiver, usbAccessoryFilter);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		
		setIntent(intent);
	}

	@Override
	protected void onPause() {
		super.onPause();

		unregisterReceiver(mUsbDeviceReceiver);
		unregisterReceiver(mUsbAccessoryReceiver);
	}
	
	private void handleIntent(Intent intent) {
		if(intent != null) {
			UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
			if(accessory != null) {
				setAccessory(accessory);
			}
		}
	}
	
	private void initScan() {
		StringBuilder sbOutput = new StringBuilder();
		
		sbOutput.append("Checking USB status...");
		
		sbOutput.append("\n\t\t");
		sbOutput.append("Loading USB manager...");
		sbOutput.append("\n\t\t");
		
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		
		if(mUsbManager != null) {
			sbOutput.append("\t");
			sbOutput.append("USB manager loaded");
			
		} else {
			sbOutput.append("\t");
			sbOutput.append("Failed to load USB manager, exiting");
			return;
		}
		
		UsbSerialDriver driver = UsbSerialProber.acquire(mUsbManager);
		if(driver != null) {
			sbOutput.append("Attempting to open usb driver...");
			sbOutput.append("\n\t\t");
			
			try {
				driver.open();
				
			} catch (IOException e1) {
				sbOutput.append("\t");
				sbOutput.append("Failed to open usb driver...");
				sbOutput.append("\n\t\t");
				sbOutput.append("Exiting");				
				sbOutput.append("\n\t\t");
				
				displayOutput(sbOutput.toString());
				
				return;
			}
		}
		
		displayOutput(sbOutput.toString());
		
		mUsbAccessories = mUsbManager.getAccessoryList();
		
		if(mUsbAccessories != null && mUsbAccessories.length > 0) {
			setAccessory(mUsbAccessories[0]);
		}
		else
			sbOutput.append("No USB accessories found");
		
		mUsbDevices = mUsbManager.getDeviceList();
		if(mUsbDevices.size() > 0) {
			setDevice(mUsbDevices.entrySet().iterator().next().getValue());
		} else {
			displayOutput("No USB devices found");
		}
	}
	
	private int getAccessoriesCount() {
		if(mUsbAccessories == null)
			mUsbAccessories = mUsbManager.getAccessoryList();
		
		if(mUsbAccessories != null)
			return mUsbAccessories.length;
		
		return 0;
	}
	
	private void setAccessory(UsbAccessory usbAccessory) {
		StringBuilder sbOutput = new StringBuilder("USB accessories: " + getAccessoriesCount());

		displayOutput(sbOutput.toString());
		sbOutput.setLength(0);
		
		for(UsbAccessory accessory : mUsbAccessories){						
			sbOutput.append("Model: "  + accessory.getModel());
			sbOutput.append("\n\t\t\t");
			sbOutput.append("Manufacturer: " + accessory.getManufacturer());
			sbOutput.append("\n\t\t\t");
			sbOutput.append("Serial #: " + accessory.getSerial());
		}
		
		mUsbAccessory = usbAccessory;
		
		openAccessory(usbAccessory, sbOutput);
		
		displayOutput(sbOutput.toString());
	}

	private void openAccessory(UsbAccessory usbAccessory, StringBuilder sbOutput) {
		
		try {
		    mFileDescriptor = mUsbManager.openAccessory(usbAccessory);
		    if (mFileDescriptor != null) {
		    	
		        FileDescriptor fd = mFileDescriptor.getFileDescriptor();
		        mInputStream = new FileInputStream(fd);
		        mOutputStream = new FileOutputStream(fd);
		    } else {
		    	sbOutput.append("\t\tUnable to open accessory handle");
		    }
		} catch (Exception e) {
			
			String message = "[" + this.toString() + "] Exception: "
					+ e.getMessage();
			
			sbOutput.append(message);
			
			System.out.println(message);
		}
	}
	
	private void sendCommandToAccessory(final String command) {
		
		StringBuilder sb = new StringBuilder("Command: " + command);
		displayOutput(sb.toString());
		
		if(mUsbAccessory == null) {
			sb.append("Accessory is not attached");
			
			displayOutput(sb.toString());
			
			return;
		}
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					mOutputStream.write(new byte[]{ (byte) command.length() });
					mOutputStream.write(command.getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
	
	private void sendCommand(String command) {
		synchronized (this) {
			if (mConnection != null) {
				
				switch(mEndPointType) {
					case Constants.USB_ENDPOINT_XFER_CONTROL:
						//mConnection.controlTransfer(
						break;
						
					case Constants.USB_ENDPOINT_XFER_BULK:
						mQueueCommand.offer(command);
						break;
				}
			}
		}
	}
	
	private void displayOutput(String message) {

		Log.d("TerminalApp", message);
		
		if (message.length() <= 0) {
			return;
		}

		StringBuilder sb = new StringBuilder();

		try {

			if (textOutput.getText().length() > 0) {
				sb.append(textOutput.getText());
				sb.append("\n");
			}
			sb.append("> " + message);

			textOutput.setText(sb.toString());

			scrollOutput.smoothScrollTo(0, textOutput.getBottom());

		} catch (Exception e) {
			message = "[" + this.toString() + "] Exception: "
					+ e.getMessage();
			System.out.println(message);
		}
	}
	
	private void setDevice(UsbDevice device) {
		
		if (mUsbDevices.containsKey(device.getDeviceName()) == false)
			mUsbDevices.put(device.getDeviceName(), device);
		
		StringBuilder sbOutput = new StringBuilder("USB Devices: "
				+ mUsbDevices.size() + "\n");

		displayOutput(sbOutput.toString());
		
		sbOutput.setLength(0);
		
		sbOutput.append("Device Name: " + device.getDeviceName());
		
		if (device.getInterfaceCount() <=0 ) {
			sbOutput.append("\t could not find interface \n\n");
			
			displayOutput(sbOutput.toString());
			
			mUsbDevices.remove(device.getDeviceName());
			
            return;
        }
		
		UsbInterface intf = device.getInterface(0);
		
		if (intf.getEndpointCount() <= 0) {
			sbOutput.append("\t could not find endpoint \n\n");
			
			displayOutput(sbOutput.toString());
			
			mUsbDevices.remove(device.getDeviceName());
			
            return;
		}
		
		UsbEndpoint ep = intf.getEndpoint(0);
		mEndPointType = ep.getType();
		
		mDevice = device;
		mUsbEndpoint = ep;
		
		getDeviceInterfaces(mDevice, sbOutput);
		
		UsbDeviceConnection connection = mUsbManager.openDevice(device);
		if (connection != null && connection.claimInterface(intf, true)) {
			mConnection = connection;
			
			Thread thread = new Thread(this);
            thread.start();
			
		} else {
			mConnection = null;
			
			displayOutput("Unable to claim device");
		}
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
		
		displayOutput(sbOutput.toString());
	}
	
	/**
	 * Broadcast Receiver for USB devices When application is being run on host
	 * device
	 */
	private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
				UsbDevice device = (UsbDevice) intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);

				if(device != null)
					setDevice(device);
				
			} else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
				UsbDevice device = (UsbDevice) intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);

				if(device != null) {
					
					if(mUsbDevices.containsKey(device.getDeviceName()) && device.getInterfaceCount() > 0 && 
							mConnection != null) {
						mConnection.releaseInterface(device.getInterface(0));
						mConnection.close();
					}
					mUsbDevices.remove(device.getDeviceName());
					
					displayOutput("USB Devices: "
							+ mUsbDevices.size());
					
					mDevice = null;
				}
			}
		}
	};

	private final BroadcastReceiver mUsbAccessoryReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			
			String action = intent.getAction();
			if(UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				
				try {
					if(mOutputStream != null)
						mOutputStream.close();
					
					if(mInputStream != null)
						mInputStream.close();
					
					mFileDescriptor.close();
					
				} catch (Exception e) {}
				
				mUsbAccessory = null;
				
				displayOutput("USB accessories: " + getAccessoriesCount());
			}
		}
	};

	@Override
	public void run() {
		UsbRequest request = new UsbRequest();
		request.initialize(mConnection, mUsbEndpoint);
		
		while (true) {
			String command = mQueueCommand.poll();
			if(command != null) {
				ByteBuffer buff = ByteBuffer.wrap(command.getBytes());
				request.queue(buff, command.getBytes().length);
				if(mConnection.requestWait() == request) {
					
					try {
	                    Thread.sleep(100);
	                } catch (InterruptedException e) {}
				} else {
					break;
				}
			}
		}
	}
}
