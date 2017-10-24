package com.realsil.ota;

import android.util.Log;

import java.util.UUID;

/**
 * ScannerServiceParser is responsible to parse scanning data and it check if scanned device has required service in it. It will get advertisement packet and check field name Service Class UUID with
 * value for 128-bit-Service-UUID = {6}
 */
public class ScannerServiceParser {
	private final String TAG = "ScannerServiceParser";
	private static final int SERVICES_MORE_AVAILABLE_16_BIT = 0x02;
	private static final int SERVICES_COMPLETE_LIST_16_BIT = 0x03;
	private static final int SERVICES_MORE_AVAILABLE_32_BIT = 0x04;
	private static final int SERVICES_COMPLETE_LIST_32_BIT = 0x05;
	private static final int SERVICES_MORE_AVAILABLE_128_BIT = 0x06;
	private static final int SERVICES_COMPLETE_LIST_128_BIT = 0x07;
	private int packetLength = 0;
	private boolean mIsValidSensor = false;
	private String mRequiredUUID;
	private static ScannerServiceParser mParserInstance;

	/**
	 * singleton implementation of ScannerServiceParser
	 */
	public static synchronized ScannerServiceParser getParser() {
		if (mParserInstance == null) {
			mParserInstance = new ScannerServiceParser();
		}
		return mParserInstance;
	}

	public boolean isValidSensor() {
		return mIsValidSensor;
	}

	/**
	 * The method will get advertisement data and required BLE Service UUID as input It will check the existence of 128 bit Service UUID field = {6} For further details on parsing BLE advertisement
	 * packet data see https://developer.bluetooth.org/Pages/default.aspx Bluetooth Core Specifications Volume 3, Part C, and Section 8
	 */
	public void decodeDeviceAdvData(byte[] data, UUID requiredUUID) throws Exception {
		mIsValidSensor = false;
		mRequiredUUID = requiredUUID.toString();
		if (data != null) {
			int fieldLength, fieldName;
			packetLength = data.length;
			for (int index = 0; index < packetLength; index++) {
				fieldLength = data[index];
				if (fieldLength == 0) {
					Log.d(TAG, "index: " + index + " No more data exist in Advertisement packet");
					return;
				}
				fieldName = data[++index];
				Log.d(TAG, "fieldName: " + fieldName + " Filed Length: " + fieldLength);

				if (fieldName == SERVICES_MORE_AVAILABLE_16_BIT || fieldName == SERVICES_COMPLETE_LIST_16_BIT) {
					Log.d(TAG, "index: " + index + " Service class 16 bit UUID exist");
					for (int i = index + 1; i < index + fieldLength - 1; i += 2)
						decodeService16BitUUID(data, i, 2);
					index += fieldLength - 1;
				} else if (fieldName == SERVICES_MORE_AVAILABLE_32_BIT || fieldName == SERVICES_COMPLETE_LIST_32_BIT) {
					Log.d(TAG, "index: " + index + " Service class 32 bit UUID exist");
					for (int i = index + 1; i < index + fieldLength - 1; i += 4)
						decodeService32BitUUID(data, i, 4);
					index += fieldLength - 1;
				} else if (fieldName == SERVICES_MORE_AVAILABLE_128_BIT || fieldName == SERVICES_COMPLETE_LIST_128_BIT) {
					Log.d(TAG, "index: " + index + " Service class 128 bit UUID exist");
					for (int i = index + 1; i < index + fieldLength - 1; i += 16)
						decodeService128BitUUID(data, i, 16);
					index += fieldLength - 1;
				} else {
					// Other Field Name						
					index += fieldLength - 1;
				}
			}
		} else {
			Log.d(TAG, "data is null!");
			return;
		}
	}

	/**
	 * check for required Service UUID inside device
	 */
	private void decodeService16BitUUID(byte[] data, int startPosition, int serviceDataLength) throws Exception {
		Log.d(TAG, "StartPosition: " + startPosition + " Data length: " + serviceDataLength);
		String serviceUUID = Integer.toHexString(decodeUuid16(data, startPosition));
		String requiredUUID = mRequiredUUID.substring(4, 8);
		Log.d(TAG, "DeviceServiceUUID: " + serviceUUID + " Required UUID: " + requiredUUID);

		if (serviceUUID.equals(requiredUUID)) {
			Log.d(TAG, "Service UUID: " + serviceUUID);
			Log.d(TAG, "Required service exist!");
			mIsValidSensor = true;
		}
	}

	/**
	 * check for required Service UUID inside device
	 */
	private void decodeService32BitUUID(byte[] data, int startPosition, int serviceDataLength) throws Exception {
		Log.d(TAG, "StartPosition: " + startPosition + " Data length: " + serviceDataLength);
		String serviceUUID = Integer.toHexString(decodeUuid16(data, startPosition + serviceDataLength - 4));
		String requiredUUID = mRequiredUUID.substring(4, 8);
		Log.d(TAG, "DeviceServiceUUID: " + serviceUUID + " Required UUID: " + requiredUUID);

		if (serviceUUID.equals(requiredUUID)) {
			Log.d(TAG, "Service UUID: " + serviceUUID);
			Log.d(TAG, "Required service exist!");
			mIsValidSensor = true;
		}
	}

	/**
	 * check for required Service UUID inside device
	 */
	private void decodeService128BitUUID(byte[] data, int startPosition, int serviceDataLength) throws Exception {
		Log.d(TAG, "StartPosition: " + startPosition + " Data length: " + serviceDataLength);
		String serviceUUID = Integer.toHexString(decodeUuid16(data, startPosition + serviceDataLength - 4));
		String requiredUUID = mRequiredUUID.substring(4, 8);
		Log.d(TAG, "DeviceServiceUUID: " + serviceUUID + " Required UUID: " + requiredUUID);

		if (serviceUUID.equals(requiredUUID)) {
			Log.d(TAG, "Service UUID: " + serviceUUID);
			Log.d(TAG, "Required service exist!");
			mIsValidSensor = true;
		}
	}

	private static int decodeUuid16(final byte[] data, final int start) {
		final int b1 = data[start] & 0xff;
		final int b2 = data[start + 1] & 0xff;

		return (b2 << 8 | b1 << 0);
	}
}
