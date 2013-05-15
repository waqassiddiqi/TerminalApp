package cs.android.terminal.util;

import cs.android.terminal.Constants;

public class MiscUtil {
	public static String getEndPointTypeName(int endPointType) {
		switch (endPointType) {
			case Constants.USB_ENDPOINT_XFER_CONTROL:
				return "zero";
	
			case Constants.USB_ENDPOINT_XFER_ISOC:
				return "isochronous";
	
			case Constants.USB_ENDPOINT_XFER_BULK:
				return "bulk";
	
			case Constants.USB_ENDPOINT_XFER_INT:
				return "interrupt";
		}
		
		return "unknown";
	}
}
