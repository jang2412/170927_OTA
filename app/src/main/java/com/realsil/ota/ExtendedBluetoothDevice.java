package com.realsil.ota;

import android.bluetooth.BluetoothDevice;

public class ExtendedBluetoothDevice {
	public BluetoothDevice device;
	public int rssi;
	public boolean isBonded;

	public ExtendedBluetoothDevice(BluetoothDevice device, int rssi, boolean isBonded) {
		this.device = device;
		this.rssi = rssi;
		this.isBonded = isBonded;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ExtendedBluetoothDevice) {
			final ExtendedBluetoothDevice that = (ExtendedBluetoothDevice) o;
			return device.getAddress().equals(that.device.getAddress());
		}
		return super.equals(o);
	}

	/**
	 * Class used as a temporary comparator to find the device in the List of {@link ExtendedBluetoothDevice}s. This must be done this way, because List#indexOf and List#contains use the parameter's
	 * equals method, not the object's from list. See {@link DeviceListAdapter#updateRssiOfBondedDevice(String, int)} for example
	 */
	public static class AddressComparator {
		public String address;

		@Override
		public boolean equals(Object o) {
			if (o instanceof ExtendedBluetoothDevice) {
				final ExtendedBluetoothDevice that = (ExtendedBluetoothDevice) o;
				return address.equals(that.device.getAddress());
			}
			return super.equals(o);
		}
	}
}
