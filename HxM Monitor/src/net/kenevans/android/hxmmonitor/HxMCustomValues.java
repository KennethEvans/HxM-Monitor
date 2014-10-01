package net.kenevans.android.hxmmonitor;

import android.bluetooth.BluetoothGattCharacteristic;

public class HxMCustomValues implements IConstants {
	long date;
	private int activity = -1;
	private int pa = -1;
	private String string;

	public HxMCustomValues(BluetoothGattCharacteristic characteristic, long date) {
		this.date = date;
		if (!characteristic.getUuid().equals(UUID_CUSTOM_MEASUREMENT)) {
			return;
		}
		int offset = 0;
		int flag = characteristic.getIntValue(
				BluetoothGattCharacteristic.FORMAT_UINT8, offset);
		offset += 1;
		if ((flag & 0x01) != 0) {
			activity = characteristic.getIntValue(
					BluetoothGattCharacteristic.FORMAT_UINT16, offset);
			offset += 2;
			string += "Activity: " + activity;
		} else {
			string += "Activity: NA";
		}
		if ((flag & 0x02) != 0) {
			pa = characteristic.getIntValue(
					BluetoothGattCharacteristic.FORMAT_UINT16, offset);
			offset += 2;
			string += "\nPeak Acceleration: " + pa;
		} else {
			string += "\nPeak Acceleration: NA";
		}
		// // DEBUG
		// final byte[] data = characteristic.getValue();
		// if (data != null && data.length > 0) {
		// final StringBuilder stringBuilder = new StringBuilder(data.length);
		// for (byte byteChar : data) {
		// stringBuilder.append(String.format("%02X ", byteChar));
		// }
		// string += "\n" + stringBuilder.toString();
		// }
	}

	/**
	 * Gets the data value.
	 * 
	 * @return
	 */
	public long getDate() {
		return date;
	}

	/**
	 * Gets the activity.
	 * 
	 * @return
	 */
	public int getActivity() {
		return activity;
	}

	/**
	 * Gets the peak acceleration.
	 * 
	 * @return
	 */
	public int getPa() {
		return pa;
	}

	/**
	 * Gets a string representation of the data in the characteristic.
	 * 
	 * @return
	 */
	public String getString() {
		return string;
	}

}
