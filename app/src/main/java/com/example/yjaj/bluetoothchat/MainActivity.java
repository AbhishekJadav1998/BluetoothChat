package com.example.yjaj.bluetoothchat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String AppName = "BluetoothChat";
    private static final UUID myUUID = UUID.fromString("5aec6f64-f01b-426c-9884-7f0ed74d2a6f");
    BluetoothAdapter myBluetoothAdapter;
    Button listen, listDevices,send;
    TextView status,msg_box;
    ListView listView;
    EditText writemsg;
    BluetoothDevice[] bluetoothDevices;
    SendRecieve sendRecieve;

    int REQUEST_ENABLE_BLUETOOTH = 1;
    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECIEVED = 5;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        findViewByIds();
        if(!myBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
        }

        ListDeviceShow();
    }

    private void ListDeviceShow() {
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<BluetoothDevice> bluetoothDevicesSet = myBluetoothAdapter.getBondedDevices();
                String[] strings = new String[bluetoothDevicesSet.size()];
                bluetoothDevices = new BluetoothDevice[bluetoothDevicesSet.size()];
                //Toast.makeText(getApplicationContext(),bluetoothDevicesSet.size(),Toast.LENGTH_SHORT).show();
                int index = 0;
                if (bluetoothDevicesSet.size() > 0){
                    for(BluetoothDevice bt : bluetoothDevicesSet){
                        bluetoothDevices[index]= bt;
                        strings[index] = bt.getName();
                        index++;
                    }
                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getApplicationContext(),android.R.layout.simple_list_item_1,strings);
                    listView.setAdapter(arrayAdapter);
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            Clients clients = new Clients(bluetoothDevices[position]);
                            clients.start();
                            status.setText("Connecting");
                        }
                    });
                }
            }
        });

        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Servers servers = new Servers();
                servers.start();
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string = String.valueOf(writemsg.getText());
                sendRecieve.write(string.getBytes());
                writemsg.setText(null);
            }
        });
    }

    public Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch(msg.what){
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;

                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;

                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;

                case STATE_CONNECTION_FAILED:
                    status.setText("Connection failed");
                    break;

                case STATE_MESSAGE_RECIEVED:
                    byte[]readBuff = (byte[]) msg.obj;
                    String temp = new String(readBuff,0,msg.arg1);
                    msg_box.setText(temp);
                    break;
            }
            return true;
        }
    });

    private void findViewByIds() {
        listen = (Button)findViewById(R.id.listen);
        listDevices = (Button) findViewById(R.id.listDevices);
        listView = (ListView) findViewById(R.id.listView);
        status = (TextView) findViewById(R.id.status);
        send = (Button) findViewById(R.id.send);
        writemsg = (EditText) findViewById(R.id.writemsg);
        msg_box = (TextView) findViewById(R.id.msg);
    }


    public class Servers extends Thread {
        private BluetoothServerSocket bluetoothServerSocket;
        public Servers(){
            try {
                bluetoothServerSocket = myBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(AppName,myUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            BluetoothSocket bluetoothSocket = null;
            while(bluetoothSocket == null){
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    bluetoothSocket= bluetoothServerSocket.accept();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(bluetoothSocket != null){
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);
                    sendRecieve = new SendRecieve(bluetoothSocket);
                    sendRecieve.start();
                    break;
                }
            }
        }
    }

    public class Clients extends  Thread{
        private BluetoothDevice Device;
        private BluetoothSocket socket;
        public Clients(BluetoothDevice device1){
            Device = device1;
            try {
                socket = Device.createRfcommSocketToServiceRecord(myUUID);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);

                sendRecieve = new SendRecieve(socket);
                sendRecieve.start();
            }
            catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }
    private class SendRecieve extends Thread{
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendRecieve(BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream = tempIn;
            outputStream = tempOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while(true){
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECIEVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
