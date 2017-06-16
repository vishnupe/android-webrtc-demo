package com.lazyfolkz.pe.webrtctest.webrtc.observers;

/**
 * Created by vishnupe on 15/6/17.
 */

public abstract class MySdpSetObserver extends MySdpObserver {

    @Override
    public void onSetSuccess() {
        doOnSetSuccess();
    }

    public abstract void doOnSetSuccess();
}
