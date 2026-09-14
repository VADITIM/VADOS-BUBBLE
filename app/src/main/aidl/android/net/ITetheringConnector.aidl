package android.net;

import android.net.IIntResultListener;
import android.net.TetheringRequestParcel;

// Binder transaction codes are the declaration order, so the five methods the platform declares first have to stand here in that order and nothing may be inserted among them; startTethering is 4 and stopTethering is 5, read off ITetheringConnector$Stub$Proxy in /apex/com.android.tethering/javalib/framework-tethering.jar on this phone. The seven that follow them there are left out because the tail cannot shift the codes of the head, and two of them name types no vendored declaration can express.
oneway interface ITetheringConnector {
    void tether(String interfaceName, String callerPackage, String attributionTag, IIntResultListener listener);
    void untether(String interfaceName, String callerPackage, String attributionTag, IIntResultListener listener);
    void setUsbTethering(boolean isOn, String callerPackage, String attributionTag, IIntResultListener listener);
    void startTethering(in TetheringRequestParcel request, String callerPackage, String attributionTag, IIntResultListener listener);
    void stopTethering(int tetheringType, String callerPackage, String attributionTag, IIntResultListener listener);
}
