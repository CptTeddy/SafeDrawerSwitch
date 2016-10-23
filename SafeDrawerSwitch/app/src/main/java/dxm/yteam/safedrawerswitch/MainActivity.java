package dxm.yteam.safedrawerswitch;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.lang.ref.WeakReference;
import java.util.Set;

public class MainActivity extends Activity {

    private static final boolean D = true;    //Debugging mode
    private static final String TAG = "SafeDrawerSwitch";

    private static final int REQUEST_ENABLE_BT = 1;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private String mConnectedDeviceName = null;
    public static BluetoothChatService mChatService = null;
    private BluetoothAdapter mBluetoothAdapter = null;

    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final Button connectBt = (Button) findViewById(R.id.connect_bt);
        final Button scanButton = (Button) findViewById(R.id.button_scan);

        connectBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Initialize array adapters. One for already paired devices and
                // one for newly discovered devices
                mPairedDevicesArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.device_name);
                mNewDevicesArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.device_name);

                // Find and set up the ListView for paired devices
                final ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
                pairedListView.setAdapter(mPairedDevicesArrayAdapter);
                pairedListView.setOnItemClickListener(mDeviceClickListener);

                // Find and set up the ListView for newly discovered devices
                final ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
                newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
                newDevicesListView.setOnItemClickListener(mDeviceClickListener);

                // Register for broadcasts when a device is discovered
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                getApplicationContext().registerReceiver(mReceiver, filter);

                // Register for broadcasts when discovery has finished
                filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                getApplicationContext().registerReceiver(mReceiver, filter);

                // Get a set of currently paired devices
                Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

                // Set a header to the list
                TextView pairedHead = new TextView(getApplicationContext());
                pairedHead.setText(R.string.title_paired_devices);
                pairedHead.setTextColor(getResources().getColor(R.color.black));
                pairedListView.addHeaderView(pairedHead);
                pairedListView.setHeaderDividersEnabled(true);
                pairedListView.setBottom(0);

                // If there are paired devices, add each one to the ArrayAdapter
                if (pairedDevices.size() > 0) {
//                    findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
//                    pairedListView.setVisibility(View.VISIBLE);
                    for (BluetoothDevice device : pairedDevices) {
                        mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    }
                } else {
                    String noDevices = getResources().getText(R.string.none_paired).toString();
                    mPairedDevicesArrayAdapter.add(noDevices);
                }

                // Set the action for scan button
                scanButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Set the header for new devices list
                        TextView otherHead = new TextView(getApplicationContext());
                        otherHead.setText(R.string.title_other_devices);
                        otherHead.setTextColor(getResources().getColor(R.color.black));
                        newDevicesListView.addHeaderView(otherHead);
                        newDevicesListView.setHeaderDividersEnabled(true);

                        // Do discovery for scanning
                        doDiscovery();
                    }
                });

                setTitle("Select or scan new");

            }
        });



    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            ensureDiscoverable();
            // Otherwise, setup the chat session
        } else {
            if (mChatService == null) {
                setupChat();
                ensureDiscoverable();
            }
        }
    }
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        // Make sure we're not doing discovery anymore
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        try {
            this.unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.toString());
        }
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
//        mOutStringBuffer = new StringBuffer("");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()");

        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // Turn on sub-title for new devices
//        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBluetoothAdapter.startDiscovery();
    }

    // The on-click listener for all devices in the ListViews
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBluetoothAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            if (info.length() <=17) {
                return;
            }
            String address = info.substring(info.length() - 17);

            // Get the BluetoothDevice object
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

            // Attempt to connect to the device
            // false means insecure connection
            mChatService.connect(device, false);

        }
    };

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String itemInfo = device.getName() + "\n" + device.getAddress();
                    if (device.getName() == null ||
                            mNewDevicesArrayAdapter.getPosition(itemInfo) != -1) {
                        return;
                    }
                    mNewDevicesArrayAdapter.add(itemInfo);

                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

    private final Handler mHandler = new MessageHandler(this);

    static class MessageHandler extends Handler {

        private final WeakReference<Activity> mActivity;

        MessageHandler(Activity activity) {
            mActivity = new WeakReference<Activity>(activity);

        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            Toast.makeText(mActivity.get(), "connected",
                                    Toast.LENGTH_SHORT).show();
                            mActivity.get().setTitle("Click Next");
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            Toast.makeText(mActivity.get(), "connecting...",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            Toast.makeText(mActivity.get(), "not connected",
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
//                case MESSAGE_WRITE:
//                    byte[] writeBuf = (byte[]) msg.obj;
//                    // construct a string from the buffer
//                    String writeMessage = new String(writeBuf);
//                    break;
//                case MESSAGE_READ:
//                    byte[] readBuf = (byte[]) msg.obj;
//                    // construct a string from the valid bytes in the buffer
//                    String readMessage = new String(readBuf, 0, msg.arg1);
//                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    String deviceName = msg.getData().getString(DEVICE_NAME);
//                    mActivity.get().setConnectedDeviceName(deviceName);
                    Toast.makeText(mActivity.get(), "Connected to "
                            + deviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(mActivity.get(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void setConnectedDeviceName(String name) {
        mConnectedDeviceName = name;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_next) {
            Intent nextIntent = new Intent(this, SwitchActivity.class);
            startActivity(nextIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
