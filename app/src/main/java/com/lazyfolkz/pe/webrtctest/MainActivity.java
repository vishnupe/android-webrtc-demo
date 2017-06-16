package com.lazyfolkz.pe.webrtctest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.lazyfolkz.pe.webrtctest.webrtc.observers.MySdpCreateObserver;
import com.lazyfolkz.pe.webrtctest.webrtc.observers.MySdpObserver;
import com.lazyfolkz.pe.webrtctest.webrtc.observers.MySdpSetObserver;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private Button start,send;
    private TextView output;
    private Socket mSocket;
    private String room = "myRoom";
    private boolean isInitiator;
    PeerConnection peerConnection;
    DataChannel mDataChannel;
    MediaConstraints constraints;
    public static final String downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        start = (Button) findViewById(R.id.start);
        send = (Button) findViewById(R.id.send);
        output = (TextView) findViewById(R.id.output);

        requestPermission();
        // Initialise sockets
        try {
            mSocket = IO.socket("http://10.7.80.3:2013");
        } catch (URISyntaxException e) {}

        // Listen for events
        mSocket.on("created",(args)->{
            runOnUiThread(() -> {
                output.append("\n Created room:" + args[0]);
            });
            isInitiator = true;
        });

        mSocket.on("joined",(args)->{
            runOnUiThread(() -> {
                output.append("\n Joined room:" + args[0]);
            });
            isInitiator = false;
        });

        mSocket.on("full",(args)->{
            runOnUiThread(() -> {
                output.append("\n Room" + args[0] + "is full");
            });
        });

        mSocket.on("ready",(args)->{
            createPeerConnection();
        });

        mSocket.on("message", (args) -> {
            runOnUiThread(() -> {
                output.append("\n Message :"+ args[0].toString());
            });
            handleSignalingMessages(args[0].toString());
        });

        //Bind to button click
        start.setOnClickListener((view) -> {
            mSocket.connect();
            mSocket.emit("create or join",room);
        });
        send.setOnClickListener((view) -> {
            sendThroughDataChannel("HIIIIIII");
            sendFileThroughDataChannel(downloadDir+"tom.jpg");
        });

    }

    private void sendMessage(JSONObject message){
        mSocket.emit("message",message);
    }

    public void sendThroughDataChannel(final String data) {

        ByteBuffer buffer = ByteBuffer.wrap(data.getBytes());
        mDataChannel.send(new DataChannel.Buffer(buffer, false));

    }

    public void sendFileThroughDataChannel(String path){
        boolean isBinaryFile = true;
        File file = new File(path); // let's assume path is a .whatever file's path (txt, jpg, pdf..)
        ByteBuffer byteBuffer = ByteBuffer.wrap(convertFileToByteArray(file));
        DataChannel.Buffer buf = new DataChannel.Buffer(byteBuffer, isBinaryFile);
        mDataChannel.send(buf);
    }

    private byte[] convertFileToByteArray(File file) {
        byte[] bArray = new byte[(int) file.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(bArray);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }
        return bArray;
    }

    private void writeBytearrayToFile(String fileName, byte[] data){
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(downloadDir+fileName);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void onChannelCreated(){
        logOnUi("Data channel created");
        mDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {

            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {

                ByteBuffer data = buffer.data;
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);

                // If it's not a binary file (text)
                if( !buffer.binary ) {
                    final String command = new String(bytes);
                    logOnUi("\n Message: "+command);
                } else {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
                    String format = simpleDateFormat.format(new Date());
                    writeBytearrayToFile("image"+format+".jpg",bytes);
                    logOnUi("\n File Received: ");
                }

            }
        });
    }

    private void handleSignalingMessages(String messageString) {
        JSONObject message = null;
        try {
            message = new JSONObject(messageString);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (message.getString("type").equals("offer")||message.getString("type").equals("OFFER")) {
                logOnUi("OFFER");
                SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm("offer"),
                        (String) message.get("sdp"));
                peerConnection.setRemoteDescription(new MySdpObserver(), sdp);
                peerConnection.createAnswer(new MySdpCreateObserver() {
                    @Override
                    public void doOnCreateSuccess(SessionDescription sessionDescription) {
                        peerConnection.setLocalDescription(new MySdpSetObserver() {
                            @Override
                            public void doOnSetSuccess() {
                                JSONObject message = new JSONObject();
                                try {
                                    message.put("type",sessionDescription.type);
                                    message.put("sdp",sessionDescription.description);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                sendMessage(message);
                            }
                        },sessionDescription);
                    }
                },constraints);
            } else if (message.getString("type").equals("answer")||message.getString("type").equals("ANSWER")) {
                logOnUi("ANSWER");
                SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm("answer"),
                        (String) message.get("sdp"));
                peerConnection.setRemoteDescription(new MySdpObserver(), sdp);

            } else if (message.getString("type").equals("candidate")) {
                logOnUi("CANDIDATE");
                IceCandidate candidate = new IceCandidate(
                        (String) message.get("id"),
                        message.getInt("label"),
                        (String) message.get("candidate"));
                peerConnection.addIceCandidate(candidate);

            } else if (message.getString("type").equals("bye")) {
                // TODO: cleanup RTC connection?
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void createPeerConnection(){

        Context context = this;
        boolean initializeAudio = true;
        boolean initializeVideo = true;
        boolean videoCodecHwAcceleration = true;
        Object renderEGLContext = null;

        // Initialise PeerConnectionFactory factory
        PeerConnectionFactory.initializeAndroidGlobals(
                context,
                initializeAudio,
                initializeVideo,
                videoCodecHwAcceleration,
                renderEGLContext);
        PeerConnectionFactory peerConnectionFactory = new PeerConnectionFactory();


        // Set ice servers
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        // Set media constraints
        constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        // Set peerconnection observer
        PeerConnection.Observer observer = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if(iceCandidate != null){
                    JSONObject message = new JSONObject();
                    try {
                        message.put("type","candidate");
                        message.put("label",iceCandidate.sdpMLineIndex);
                        message.put("id",iceCandidate.sdpMid);
                        message.put("candidate",iceCandidate.sdp);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    sendMessage(message);
                }
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                logOnUi("OnDataChanel");
                mDataChannel = dataChannel;
                onChannelCreated();
            }

            @Override
            public void onRenegotiationNeeded() {

            }
        };

        // Initialise peerConnection
        peerConnection = peerConnectionFactory.createPeerConnection(
                iceServers,
                constraints,
                observer);

        if(isInitiator){
            DataChannel.Init dcInit = new DataChannel.Init();
            dcInit.id = 1;
            mDataChannel = peerConnection.createDataChannel("1", dcInit);
            onChannelCreated();
            peerConnection.createOffer(new MySdpCreateObserver() {
                @Override
                public void doOnCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new MySdpSetObserver() {
                        @Override
                        public void doOnSetSuccess() {
                            JSONObject message = new JSONObject();
                            try {
                                message.put("type",sessionDescription.type);
                                message.put("sdp",sessionDescription.description);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            sendMessage(message);
                        }
                    },sessionDescription);
                }
            },constraints);
        }

    }
    private void logOnUi(String message){
        runOnUiThread(() -> {
            output.append("\n LOG :"+ message);
        });
    }
    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (result == PackageManager.PERMISSION_GRANTED){
            return true;
        } else {
            return false;
        }
    }

    public void requestPermission() {
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Intent intent = new Intent(this, DownloadService.class);
//                    startService(intent);
                } else {
//                    Snackbar.make(findViewById(R.id.coordinatorLayout),"Permission Denied, Please allow to proceed !", Snackbar.LENGTH_LONG).show();
                }
                break;
        }
    }

}