package android.net

import android.os.Parcel
import android.os.Parcelable


// A stable AIDL parcelable carries its own length ahead of its fields, and the three LinkAddress / SoftApConfiguration members name classes the SDK does not publish — so the request is written by hand in the order TetheringRequestParcel.writeToParcel has on this phone, with each of those three sent as the zero a null typed object writes.
class TetheringRequestParcel(
    private val requestType: Int,
    private val tetheringType: Int,
    private val exemptFromEntitlementCheck: Boolean,
    private val showProvisioningUi: Boolean,
    private val connectivityScope: Int,
    private val uid: Int
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val start = parcel.dataPosition()
        parcel.writeInt(0)
        parcel.writeInt(requestType)
        parcel.writeInt(tetheringType)
        parcel.writeInt(0)
        parcel.writeInt(0)
        parcel.writeBoolean(exemptFromEntitlementCheck)
        parcel.writeBoolean(showProvisioningUi)
        parcel.writeInt(connectivityScope)
        parcel.writeInt(0)
        parcel.writeInt(uid)
        parcel.writeString(null)
        parcel.writeString(null)
        val end = parcel.dataPosition()
        parcel.setDataPosition(start)
        parcel.writeInt(end - start)
        parcel.setDataPosition(end)
    }

    // The generated ITetheringConnector stub reads this type as well as writing it, so the class does not compile without a CREATOR even though only the outbound half is ever used here.
    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<TetheringRequestParcel> {
            override fun createFromParcel(parcel: Parcel): TetheringRequestParcel {
                val start = parcel.dataPosition()
                val length = parcel.readInt()
                val requestType = parcel.readInt()
                val tetheringType = parcel.readInt()
                parcel.readInt()
                parcel.readInt()
                val exemptFromEntitlementCheck = parcel.readBoolean()
                val showProvisioningUi = parcel.readBoolean()
                val connectivityScope = parcel.readInt()
                parcel.readInt()
                val uid = parcel.readInt()
                parcel.setDataPosition(start + length)
                return TetheringRequestParcel(
                    requestType,
                    tetheringType,
                    exemptFromEntitlementCheck,
                    showProvisioningUi,
                    connectivityScope,
                    uid
                )
            }

            override fun newArray(size: Int) = arrayOfNulls<TetheringRequestParcel>(size)
        }
    }
}
