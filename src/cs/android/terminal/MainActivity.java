package cs.android.terminal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;


import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Parcel;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.drm.DrmStore.Action;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.support.v4.app.NavUtils;


/**
 * 
 * @author Jeffrey
 *
 */
public class MainActivity extends Activity {

	private TextView textOutput = null;
	private EditText editInput = null;
	private Button buttonExecute = null;
	private ScrollView scrollOutput = null;
	private UsbAccessory[] accessories = new UsbAccessory[0];
	private HashMap<String, UsbDevice> usbDevices = null;
	private HashMap<String, Integer> commandHash = new HashMap<String, Integer>();
	private Timer timer;
	private UsbEndpoint mUsbEndpoint = null;
	
	public UsbReceiver usbReceiver = new UsbReceiver();
	
	public static final String ACTION_USB_PERMISSION = "cs.android.terminal.USB_PERMISSION";
	public static final String ACTION_TIMEOUT = "cs.android.terminal.TIMEOUT";
	public static final String ACTION_MESSAGE = "cs.android.terminal.ERROR";
	public static final IntentFilter filterUSBDevice = new IntentFilter("android.hardware.usb.action.USB_DEVICE_ATTACHED"); //"android.hardware.usb.action.USB_DEVICE_ATTACHED");
	private static final int CMD_SEND = 0;  
	private static final int CMD_QUIT = 1;
	private String action = "";
	public SharedPreferences prefs;
	private String output = "";
	
	public static final int USB_DIR_IN = 128;
	public static final int USB_DIR_OUT = 0;
	
	public static final int USB_ENDPOINT_XFER_CONTROL = 0;
	public static final int USB_ENDPOINT_XFER_ISOC = 1;
	public static final int USB_ENDPOINT_XFER_BULK = 2;
	public static final int USB_ENDPOINT_XFER_INT = 3;
	
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);      
                
        // populate command hash table
        commandHash.put("SEND", CMD_SEND);
        commandHash.put("QUIT", CMD_QUIT);
        
        
    }
       
    @Override
	protected void onPause() {		
		super.onPause();
				
		Editor editor = prefs.edit();
		editor.putString("cs.android.terminal.USER_PREFS_OUTPUT", textOutput.getText().toString());
		editor.commit();
		
//		try{unregisterReceiver(usbReceiver);}catch(Exception e){}
		try{timer.cancel();}catch(Exception e){}		
		
	}
    
	@Override
	protected void onResume() {		
		super.onResume();
			
		action = getIntent().getAction();
		
		try {
			
			prefs = getSharedPreferences("cs.android.terminal.USER_PREFS",Activity.MODE_PRIVATE);	
	        output = prefs.getString("cs.android.terminal.USER_PREFS_OUTPUT", "");
	        
			
//			IntentFilter filterUSBPermission = new IntentFilter(ACTION_USB_PERMISSION);
			
//			IntentFilter filterUSBAccessory = new IntentFilter("android.hardware.usb.action.USB_ACCESSORY_ATTACHED");
//			IntentFilter filterTimeout = new IntentFilter(ACTION_TIMEOUT);
//			IntentFilter filterMediaScanFinished = new IntentFilter("android.intent.action.MEDIA_SCANNER_FINISHED");
			IntentFilter filterError = new IntentFilter("cs.android.terminal.ERROR");
//			
//			registerReceiver(usbReceiver, filterUSBPermission);
			registerReceiver(usbReceiver, filterUSBDevice);
			registerReceiver(usbReceiver, filterError);
//			registerReceiver(usbReceiver, filterTimeout);
//			registerReceiver(usbReceiver, filterMediaScanFinished);
			
			
			PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

		} catch (Exception e1) {
			String message = "["+this.toString()+".loadControls] Exception: "+e1.getMessage();
			displayOutput(message);
		}
								
		loadControls();
        usbEnum();
     
        
		
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}
	

	/**
     * {@inheritDoc}
     */
	private void loadControls(){
		
		try {
			
			// get view handles
			textOutput = (TextView) findViewById(R.id.txtOutput);
			editInput = (EditText) findViewById(R.id.txtInput);
			buttonExecute = (Button) findViewById(R.id.btnExecute);
			scrollOutput = (ScrollView) findViewById(R.id.layoutOutputScroll);
			if(!action.equalsIgnoreCase("android.hardware.usb.action.USB_DEVICE_ATTACHED")){				
				textOutput.setText("");
				displayOutput("Terminal loading");			
			}else{
				textOutput.setText(output);
			}
			
			buttonExecute.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if(editInput.getText().toString().length()>0){
						processCommand(editInput.getText().toString());											
					}					
					return;
				}

			});
			
		} catch (Exception e) {
			String message = "["+this.toString()+".loadControls] Exception: "+e.getMessage();
			displayOutput(message);
		}	

	}
	
	/**
	 * 
	 * @param message
	 */
    private void displayOutput(String message) {
    	
    	if(message.length()<=0){
    		return;
    	}
    	
    	StringBuilder sb = new StringBuilder();
    	
    	try {

    		if(textOutput.getText().length()>0){
    			sb.append(textOutput.getText());
        		sb.append("\n");
    		}    		
    		sb.append("> "+message);
//    		sb.append("\n");
//    		sb.append(">");
    		textOutput.setText(sb.toString());
    		
    		scrollOutput.pageScroll(0x00000082);
			
		} catch (Exception e) {
			message = "["+this.toString()+".usbEnum] Exception: "+e.getMessage();
			System.out.println(message);
		}
		
	}

    /**
     * 
     */
	private void usbEnum(){
    	
//		displayOutput("action: "+action);
		
		StringBuilder sb = new StringBuilder();
		sb.append("Checking USB status...");
		
		// Get UsbManager from Android.
		UsbManager manager;
		// Find the first available driver.
		UsbSerialDriver driver;
		
		try {
		
			sb.append("\n\t\t");
			sb.append("Loading USB manager...");
			sb.append("\n\t\t");
			manager = (UsbManager) getSystemService(Context.USB_SERVICE);		
			
			if(manager != null){
				sb.append("\t");
				sb.append("USB manager loaded");
				sb.append("\n\t\t");
			}
			
			
			sb.append("Loading USB driver...");
			sb.append("\n\t\t");
			// if arduino board then load driver
			driver = UsbSerialProber.acquire(manager);
						
			if (driver != null) {
				
				sb.append("Attempting to open usb driver...");
				sb.append("\n\t\t");
				try {
					driver.open();
					
				} catch (IOException e1) {
					sb.append("\t");
					sb.append("Failed to open usb driver...");
					sb.append("\n\t\t");
					sb.append("Exiting");				
					sb.append("\n\t\t");
					return;
				}
				
				try {
					
					sb.append("Setting baud rate");
					sb.append("\n\t\t");
					driver.setBaudRate(115200);

					byte buffer[] = new byte[16];
					int numBytesRead = driver.read(buffer, 1000);
					
					sb.append("Read " + numBytesRead + " bytes.");
					sb.append("\n\t\t");
					
					// extract the usb devices
					accessories = manager.getAccessoryList();					
					for(UsbAccessory accessory : accessories){						
						sb.append("Model: "+accessory.getModel());
						sb.append("\n\t\t");
						sb.append("Manufacturer: "+accessory.getManufacturer());
						sb.append("\n\t\t");
						sb.append("Serial #: "+accessory.getSerial());					
						
					}		
					
					usbDevices = manager.getDeviceList();
					
				} catch (IOException e) {
					String message = "["+this.toString()+".usbEnum] Exception: "+e.getMessage();
					displayOutput(message);				
				} finally {
					try {
						driver.close();
					} catch (IOException e) {					
					}
							
				}
							
			}else{
				// driver is null
				sb.append("\t");
				sb.append("Failed to load USB driver.");
				
				
				displayOutput(sb.toString());
				sb = new StringBuilder();
				
				if (manager != null) {
					// extract the usb devices
					accessories = manager.getAccessoryList();
					if(accessories != null){
						for(UsbAccessory accessory : accessories){						
							sb.append("Model: "+accessory.getModel());
							sb.append("\n\t\t");
							sb.append("Manufacturer: "+accessory.getManufacturer());
							sb.append("\n\t\t");
							sb.append("Serial #: "+accessory.getSerial());					
							
						}		
					}
					
					usbDevices = manager.getDeviceList();
														
					
				}
					
				
				
			}
			
			
			try {
	        	if(accessories != null){
	        		if(accessories.length > 0){
	        			displayOutput(accessories.length+" USB accessories found");
	        		}else{
	        			displayOutput("No USB accessories found");
	        		}
	        	}else{
	        		displayOutput("No USB accessories found");
	        	}
				
				if(accessories.length ==0){
					//buttonExecute.setTextColor(R.)
				}
				
			} catch (Exception e) {
				
			}		
			
			if(usbDevices != null){
				if(!usbDevices.isEmpty()){						
					displayOutput("USB Devices: "+usbDevices.size());					
					//printMap(usbDevices);
					
					Intent intent = getIntent();
					UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					sb = new StringBuilder();
					sb.append("Device Name: "+device.getDeviceName());					
					
					for(int idx = 0 ; idx < device.getInterfaceCount(); idx++){
						UsbInterface usbInterface = device.getInterface(idx);
						sb.append("\n\t\tUSB Interface: "+usbInterface.getId());

						for(int ndx = 0 ; ndx < usbInterface.getEndpointCount(); ndx++){
							UsbEndpoint usbEndpoint = usbInterface.getEndpoint(ndx);
							String direction = "in";
							switch(usbEndpoint.getDirection()){							
								case USB_DIR_OUT:{
									direction = "out";
									break;
								}								
								case USB_DIR_IN :{
									direction = "in";
									break;
								}
							
							}
							
							String type = "";
							switch(usbEndpoint.getType()){							
							case USB_ENDPOINT_XFER_CONTROL:{
								type = "zero";
								break;
							}								
							case USB_ENDPOINT_XFER_ISOC :{
								type = "isochronous";
								break;
							}
							case USB_ENDPOINT_XFER_BULK :{
								type = "bulk";
								break;
							}
							case USB_ENDPOINT_XFER_INT :{
								type = "interrupt";
								break;
							}
						
						}
							
							if(usbInterface.getId()==0 && usbEndpoint.getEndpointNumber()==1){
								mUsbEndpoint = usbEndpoint;
							}
							
							sb.append("\n\t\t\tEndpoint: "+usbEndpoint.getEndpointNumber()+" ["+direction+"] "+" "+type+" at "+usbEndpoint.getAddress());
							
						}
						
					}
					
					displayOutput(sb.toString());
					
//					UsbDeviceConnection usbDeviceConnection = manager.get
					
					sb = new StringBuilder();
					
					
				}else{
					displayOutput("No USB devices found");
				}
			}else{
				displayOutput("No USB devices found");
			}
						
		} catch (Exception e2) {
			String message = "["+this.toString()+".usbEnum] Exception: "+e2.getMessage();
			displayOutput(message);		
		} finally {
			
			// display results
			displayOutput(sb.toString());	
						
		}
				
    	
    }
	
	/**
	 * 
	 * @param command
	 */
	private void processCommand(final String enteredText){
		
		String command = "";
		String parms = "";
		
		try {
			// parse command
			if(enteredText.indexOf(" ") > 0){
				command = enteredText.substring(0, enteredText.indexOf(" "));	
			}else{
				command = enteredText;
			}
			

			// send data on a worker thread
			new Thread(new Runnable() {
		        public void run() {			        	
		        			        	
		        	try {
		        		//!SEL_LF 
		        		final Parcel parcel = Parcel.obtain();
		        		parcel.writeValue(enteredText);
//		        	    final byte[] bytes = parcel.marshall();
		        		
						mUsbEndpoint.writeToParcel(parcel, 0);
						
						Intent intent = new Intent(ACTION_MESSAGE);
						Bundle bundle = new Bundle();
					    bundle.putString(ACTION_MESSAGE, "THIS IS A TEST OF THE EMERGENCY BROADCAST SYSTEM - THIS IS ONLY A TEST");		    
					    intent.putExtras(bundle);
						sendBroadcast(intent);
						
						
					} catch (Exception e) {
						String message = "["+this.toString()+".processCommand.thread] Exception: "+e.getMessage();
						Intent intent = new Intent(ACTION_MESSAGE);
						Bundle bundle = new Bundle();
					    bundle.putString(ACTION_MESSAGE, message);		    
					    intent.putExtras(bundle);
						sendBroadcast(intent);
					}
		        	
		        	
		        	
		        }
		    }).start();

			
			
			
			
		} catch (Exception e) {
			if(command.length() == 0){
				displayOutput("Unrecognized command '"+enteredText+"'");
			}else{
				displayOutput("Unrecognized command '"+command+"'");	
			}
//			String message = "["+this.toString()+".processCommand] Exception: "+e.getMessage();
//			displayOutput(message);
		}
		
		
		
		
	}
	
	/**
	 * 
	 * @param mp
	 */
	private void printMap(Map mp) {
		
		StringBuilder sb = new StringBuilder();
		
	    Iterator it = mp.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pairs = (Map.Entry)it.next();
	        sb.append(pairs.getKey() + " = " + pairs.getValue()+"");
	        it.remove(); // avoids a ConcurrentModificationException
	    }
	    
	    displayOutput(sb.toString());
	    
	}
	
	
	public class UsbReceiver extends BroadcastReceiver {
		
	    public void onReceive(Context context, Intent intent) {
	       
	    	
//	    	displayOutput("USB receiver fired");
	    	
	    	String action = intent.getAction();
	    	if (action.equals(ACTION_TIMEOUT)) {
	    		displayOutput("Timeout - No response");	    		 
	    	}else{
	    		if (action.equals(ACTION_MESSAGE)) {
	    			String error = intent.getStringExtra(ACTION_MESSAGE);
	    			displayOutput(error);
	    		}else{
	    			usbEnum();
	    		}
	    	}	    			    	
	    	
	    }
	};
	
    
}












//if (ACTION_USB_PERMISSION.equals(action)) {
//    synchronized (this) {
//        UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
//
//        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
//            if(accessory != null){
//                //call method to set up accessory communication
//            }
//        }
//        else {
//            Log.d(this.toString(), "permission denied for accessory " + accessory);
//        }
//    }
//}






//if(command.length()>0){
//displayOutput("Command: "+command);
//	if(command.length()< enteredText.length()){
//		parms = enteredText.substring(enteredText.indexOf(" ")+1, enteredText.length());
//	}
//}
//if(command.length()>0){
//	switch(commandHash.get(command.toUpperCase())){		
//		case CMD_SEND:{
//			displayOutput(command.toUpperCase()+" "+parms+"");
//			break;
//		}
//		case CMD_QUIT:{
//			finish();
//			break;
//		}
//		default: {
//			displayOutput("Unrecognized command '"+command+"'");
//		}			
//	}//switch
//}else{
//	if(command.length() == 0){
//		displayOutput("Unrecognized command '"+enteredText+"'");
//	}else{
//		displayOutput("Unrecognized command '"+command+"'");	
//	}
//	
//}


//timer = new Timer();
//timer.schedule(new TimerTask() {
//	@Override
//	public void run() {	
//												
//		try {
////			System.out.println("["+this.toString()+".TIMER FIRED] CHECKPOINT ");
//			Intent intent = new Intent(ACTION_TIMEOUT);
//			sendBroadcast(intent);
//			
//		} catch (Exception e) {
//			System.out.println("["+this.toString()+".FAILSAFE FIRED] Exception: "+e.getMessage());
//		}
//	}
//}, 2000, 2000);





//static jint android_hardware_UsbDeviceConnection_bulk_request(JNIEnv *env, jobject thiz,
//        jint endpoint, jbyteArray buffer, jint length, jint timeout)
//{
//    struct usb_device* device = get_device_from_object(env, thiz);
//    if (!device) {
//        LOGE("device is closed in native_control_request");
//        return -1;
//    }
//
//    jbyte* bufferBytes = NULL;
//    if (buffer) {
//        if (env->GetArrayLength(buffer) < length) {
//            jniThrowException(env, "java/lang/ArrayIndexOutOfBoundsException", NULL);
//            return -1;
//        }
//        bufferBytes = env->GetByteArrayElements(buffer, 0);
//    }
//
//    jint result = usb_device_bulk_transfer(device, endpoint, bufferBytes, length, timeout);
//
//    if (bufferBytes)
//        env->ReleaseByteArrayElements(buffer, bufferBytes, 0);
//
//    return result;
//}
//
//Which then calls the usb_device_bulk_transfer() in the usbhost.h lib. which looks like this:
//
//int usb_device_bulk_transfer(struct usb_device *device,
//                            int endpoint,
//                            void* buffer,
//                            int length,
//                            unsigned int timeout)
//{
//    struct usbdevfs_bulktransfer  ctrl;
//
//    // need to limit request size to avoid EINVAL
//    if (length > MAX_USBFS_BUFFER_SIZE)
//        length = MAX_USBFS_BUFFER_SIZE;
//
//    memset(&ctrl, 0, sizeof(ctrl));
//    ctrl.ep = endpoint;
//    ctrl.len = length;
//    ctrl.data = buffer;
//    ctrl.timeout = timeout;
//    return ioctl(device->fd, USBDEVFS_BULK, &ctrl);
//}



	